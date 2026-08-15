import { describe, expect, test, beforeEach, mock } from "bun:test"
import { makeConfig } from "./fixtures"

interface TestItemNode {
  id: string
  content: { __typename: string; title: string; number?: number }
  fieldValues: { nodes: Array<{ name?: string; date?: string; field: { name: string } }> }
}

interface ItemsPageLike {
  node: { items: { nodes: TestItemNode[]; pageInfo: { hasNextPage: boolean; endCursor: string | null } } }
}

function makeNode(partial: {
  id?: string
  title?: string
  type?: "DraftIssue" | "Issue"
  number?: number
  fields?: Record<string, string>
}): TestItemNode {
  const fields = partial.fields ?? {}
  return {
    id: partial.id ?? "PVTI_x",
    content: {
      __typename: partial.type ?? "DraftIssue",
      title: partial.title ?? "Título",
      ...(partial.number !== undefined ? { number: partial.number } : {}),
    },
    fieldValues: {
      nodes: Object.entries(fields).map(([name, value]) => ({ name: value, field: { name } })),
    },
  }
}

function page(nodes: TestItemNode[], hasNextPage = false, endCursor: string | null = null): ItemsPageLike {
  return { node: { items: { nodes, pageInfo: { hasNextPage, endCursor } } } }
}

let allPagesQueue: ItemsPageLike[] = []

const gqlMock = mock(async (query: string, _vars: Record<string, unknown>) => {
  if (query.includes("items(first: 100")) {
    const p = allPagesQueue.shift()
    if (!p) throw new Error("mock: cola vacía para fetchAllItems")
    return p
  }
  throw new Error(`mock: query no contemplada: ${query.slice(0, 60)}`)
})

mock.module("./client", () => ({ gql: gqlMock }))

const { findItems } = await import("./list")

beforeEach(() => {
  allPagesQueue = []
  gqlMock.mockClear()
})

describe("findItems — búsqueda por substring", () => {
  test("encuentra por substring (case-insensitive)", async () => {
    allPagesQueue = [
      page([
        makeNode({ id: "A", title: "Sesión de trabajo" }),
        makeNode({ id: "B", title: "Mejora del CLI" }),
      ]),
    ]
    const items = await findItems(makeConfig(), "sesión")
    expect(items.map((i) => i.id)).toEqual(["A"])
  })

  test("devuelve múltiples coincidencias", async () => {
    allPagesQueue = [
      page([
        makeNode({ id: "A", title: "Sesión de trabajo" }),
        makeNode({ id: "B", title: "Sesión de prueba" }),
        makeNode({ id: "C", title: "Otra cosa" }),
      ]),
    ]
    const items = await findItems(makeConfig(), "sesión")
    expect(items.map((i) => i.id)).toEqual(["A", "B"])
  })

  test("devuelve vacío si no hay coincidencias", async () => {
    allPagesQueue = [page([makeNode({ id: "A", title: "Sesión" })])]
    const items = await findItems(makeConfig(), "inexistente")
    expect(items).toHaveLength(0)
  })

  test("recorre todas las páginas (paginado)", async () => {
    allPagesQueue = [
      page([makeNode({ id: "A", title: "Sesión de trabajo" })], true, "cursor1"),
      page([makeNode({ id: "B", title: "Sesión de prueba" })], false, null),
    ]
    const items = await findItems(makeConfig(), "sesión")
    expect(items.map((i) => i.id)).toEqual(["A", "B"])
  })
})

describe("findItems — modo exacto", () => {
  test("exact: solo coincide el título idéntico", async () => {
    allPagesQueue = [
      page([
        makeNode({ id: "A", title: "Sesión" }),
        makeNode({ id: "B", title: "Sesión de trabajo" }),
      ]),
    ]
    const items = await findItems(makeConfig(), "Sesión", { exact: true })
    expect(items.map((i) => i.id)).toEqual(["A"])
  })

  test("exact: ignora mayúsculas/minúsculas", async () => {
    allPagesQueue = [page([makeNode({ id: "A", title: "Sesión de trabajo" })])]
    const items = await findItems(makeConfig(), "sesión de trabajo", { exact: true })
    expect(items.map((i) => i.id)).toEqual(["A"])
  })
})
