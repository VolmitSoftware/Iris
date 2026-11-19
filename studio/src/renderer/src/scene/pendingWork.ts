/**
 * How far along work is when only what is still left is known: measured against the most that was left at once since
 * the work was last done.
 */
export class PendingWork {
  private mostPending = 0

  /** @return 0 to 1, where 1 means nothing is left */
  completion(pending: number): number {
    if (pending <= 0) {
      this.mostPending = 0
      return 1
    }
    this.mostPending = Math.max(this.mostPending, pending)
    return 1 - pending / this.mostPending
  }
}
