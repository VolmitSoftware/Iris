const KEPT_STEPS = 100

/**
 * The texts a file had before each edit, so undoing writes an earlier text back whole. A step is only taken once its
 * write succeeded, so a refused undo leaves the history as it was.
 */
export class EditHistory {
  private readonly undos: string[] = []
  private readonly redos: string[] = []

  get canUndo(): boolean {
    return this.undos.length > 0
  }

  get canRedo(): boolean {
    return this.redos.length > 0
  }

  edited(before: string): void {
    this.undos.push(before)
    if (this.undos.length > KEPT_STEPS) {
      this.undos.shift()
    }
    this.redos.length = 0
  }

  /** The text undoing writes, if there is one. */
  nextUndo(): string | undefined {
    return this.undos.at(-1)
  }

  undone(before: string): void {
    this.undos.pop()
    this.redos.push(before)
  }

  nextRedo(): string | undefined {
    return this.redos.at(-1)
  }

  redone(before: string): void {
    this.redos.pop()
    this.undos.push(before)
  }
}
