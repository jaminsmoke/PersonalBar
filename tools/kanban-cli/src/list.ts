import { gql } from "./client"
import type { KanbanConfig } from "./config"

interface ListFilters {
  status?: string
  version?: string
  tipo?: string
  area?: string
  limit?: number
}

interface ListItem {
  id: string
  title: string
  type: "DraftIssue" | "Issue"
  number?: number
  status?: string
  version?: string
  tipo?: string
  area?: string
}

interface FindOptions {
  exact?: boolean
}

interface ItemNode {
  id: string
  content: { __typename: string; title: string; number?: number }
  fieldValues: {
    nodes: Array<{
      name?: string
      date?: string
      field: { name: string }
    }>
  }
}

interface ItemsPage {
  node: {
    items: {
      nodes: ItemNode[]
      pageInfo: { hasNextPage: boolean; endCursor: string | null }
    }
  }
}

const PAGE_SIZE = 100

/**
 * Fetch all kanban items, paginating through the project until exhausted.
 * Returns them in the project's ordering (position on the board).
 */
async function fetchAllItems(projectId: string): Promise<ItemNode[]> {
  const all: ItemNode[] = []
  let cursor: string | null = null

  while (true) {
    const data: ItemsPage = await gql(
      `query($projectId: ID!, $cursor: String) {
        node(id: $projectId) {
          ... on ProjectV2 {
            items(first: ${PAGE_SIZE}, after: $cursor) {
              nodes {
                id
                content { __typename, ... on DraftIssue { title } ... on Issue { title, number } }
                fieldValues(first: 20) {
                  nodes {
                    ... on ProjectV2ItemFieldSingleSelectValue { name, field { ... on ProjectV2FieldCommon { name } } }
                    ... on ProjectV2ItemFieldDateValue { date, field { ... on ProjectV2FieldCommon { name } } }
                  }
                }
              }
              pageInfo { hasNextPage, endCursor }
            }
          }
        }
      }`,
      { projectId, cursor }
    )

    all.push(...data.node.items.nodes)
    if (!data.node.items.pageInfo.hasNextPage) break
    if (!data.node.items.pageInfo.endCursor) break // safety: avoid infinite loop
    cursor = data.node.items.pageInfo.endCursor
  }

  return all
}

/**
 * List kanban items with optional filters.
 *
 * When filters are active, all items are fetched (paginated) before filtering,
 * so results are correct regardless of where the matching items sit on the board.
 * Without filters, only `limit` items are fetched.
 */
export async function listItems(cfg: KanbanConfig, filters: ListFilters = {}): Promise<ListItem[]> {
  const hasFilters = Boolean(filters.status || filters.version || filters.tipo || filters.area)

  const nodes = hasFilters
    ? await fetchAllItems(cfg.projectId)
    : (await fetchItems(cfg.projectId, filters.limit ?? 50)).node.items.nodes

  const items: ListItem[] = []

  for (const node of nodes) {
    const item = toListItem(node)

    // Apply filters
    if (filters.status && item.status !== filters.status) continue
    if (filters.version && item.version !== filters.version) continue
    if (filters.tipo && item.tipo !== filters.tipo) continue
    if (filters.area && item.area !== filters.area) continue

    items.push(item)
  }

  return items
}

/**
 * Map a raw GraphQL item node to a ListItem.
 * Shared by listItems (filters) and findItems (title search).
 */
function toListItem(node: ItemNode): ListItem {
  const ct = node.content as { __typename: string; title: string; number?: number }
  const fv: Record<string, string> = {}
  for (const v of node.fieldValues.nodes) {
    if (v.name) fv[v.field.name] = v.name
    if (v.date) fv[v.field.name] = v.date
  }

  return {
    id: node.id,
    title: ct.title,
    type: ct.__typename as "DraftIssue" | "Issue",
    number: ct.number,
    status: fv["Status"] ?? "-",
    version: fv["Versión"] ?? fv["Version"] ?? "-",
    tipo: fv["Tipo"] ?? "-",
    area: fv["Área principal"] ?? fv["Area"] ?? "-",
  }
}

/**
 * Search all kanban items by title.
 *
 * Matches case-insensitively: substring by default, exact equality with `exact`.
 * Always fetches the full board (paginated), so results are correct regardless
 * of where the matching items sit.
 */
export async function findItems(cfg: KanbanConfig, query: string, opts: FindOptions = {}): Promise<ListItem[]> {
  const nodes = await fetchAllItems(cfg.projectId)
  const q = query.trim().toLowerCase()

  return nodes
    .map(toListItem)
    .filter((item) => {
      const title = item.title.toLowerCase()
      return opts.exact ? title === q : title.includes(q)
    })
}

/**
 * Fetch a single page of items (used when no filters are active).
 */
async function fetchItems(projectId: string, limit: number): Promise<ItemsPage> {
  return gql(
    `query($projectId: ID!, $limit: Int!) {
      node(id: $projectId) {
        ... on ProjectV2 {
          items(first: $limit) {
            nodes {
              id
              content { __typename, ... on DraftIssue { title } ... on Issue { title, number } }
              fieldValues(first: 20) {
                nodes {
                  ... on ProjectV2ItemFieldSingleSelectValue { name, field { ... on ProjectV2FieldCommon { name } } }
                  ... on ProjectV2ItemFieldDateValue { date, field { ... on ProjectV2FieldCommon { name } } }
                }
              }
            }
            pageInfo { hasNextPage, endCursor }
          }
        }
      }
    }`,
    { projectId, limit }
  )
}
