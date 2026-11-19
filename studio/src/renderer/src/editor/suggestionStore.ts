import { sourceKey, type ListedSource } from './suggestions'

/** What the server lists for the suggestions of a source that names platform or pack entries. */
export type SuggestionFetch = (of: { reference: string } | { resource: string }) => Promise<string[]>

/** The suggestion lists, each asked for once; a list that could not be read is asked for again next time. */
export class SuggestionStore {
  private readonly lists = new Map<string, Promise<string[]>>()

  constructor(private readonly fetch: SuggestionFetch) {}

  load(source: ListedSource): Promise<string[]> {
    const key = sourceKey(source)
    const known = this.lists.get(key)
    if (known !== undefined) {
      return known
    }
    const asked = this.fetch(source.from === 'reference' ? { reference: source.reference } : { resource: source.resource })
    this.lists.set(key, asked)
    asked.catch(() => this.lists.delete(key))
    return asked
  }
}
