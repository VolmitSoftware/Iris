import { describe, expect, it } from 'vitest'
import { EditHistory } from './EditHistory'

describe('EditHistory', () => {
  it('undoes back to the text before an edit and redoes forward again', () => {
    const history = new EditHistory()
    history.edited('a')

    expect(history.nextUndo()).toBe('a')
    history.undone('b')
    expect(history.canUndo).toBe(false)
    expect(history.nextRedo()).toBe('b')
    history.redone('a')
    expect(history.nextUndo()).toBe('a')
  })

  it('forgets what could be redone once a new edit is made', () => {
    const history = new EditHistory()
    history.edited('a')
    history.undone('b')

    history.edited('a')

    expect(history.canRedo).toBe(false)
  })

  it('keeps the history as it was when an undo is never written', () => {
    const history = new EditHistory()
    history.edited('a')

    history.nextUndo()

    expect(history.canUndo).toBe(true)
  })
})
