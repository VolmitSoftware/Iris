import { describe, expect, it } from 'vitest'
import { idOf } from './nodeLayout'
import { pathAfterRemoval, positionsAfterEdit } from './nodePositions'

const at = { x: 44, y: 88 }

describe('pathAfterRemoval', () => {
  it('moves an element after the removed one, and what it holds, up an index', () => {
    expect(pathAfterRemoval(['parts', 2, 'children', 0], ['parts', 1])).toEqual(['parts', 1, 'children', 0])
  })

  it('leaves an element before it and one of another array alone', () => {
    expect(pathAfterRemoval(['parts', 0], ['parts', 1])).toEqual(['parts', 0])
    expect(pathAfterRemoval(['materials', 3], ['parts', 1])).toEqual(['materials', 3])
  })
})

describe('positionsAfterEdit', () => {
  it('drops a deleted node and what grows from it, and moves the later ones up', () => {
    const positions = { [idOf(['parts', 0])]: at, [idOf(['parts', 0, 'children', 0])]: at, [idOf(['parts', 1])]: at }

    expect(positionsAfterEdit(positions, { op: 'delete', path: ['parts', 0] }, {})).toEqual({ [idOf(['parts', 0])]: at })
  })

  it('keeps a moved node where it was, under the path it gets at the end of the list it joins', () => {
    const values = { parts: [{}, { children: [{}] }] }
    const positions = { [idOf(['parts', 0])]: at, [idOf(['parts', 0, 'children', 0])]: { x: 0, y: 0 } }

    expect(positionsAfterEdit(positions, { op: 'move', path: ['parts', 0], toList: ['parts', 1, 'children'] }, values)).toEqual({
      [idOf(['parts', 0, 'children', 1])]: at,
      [idOf(['parts', 0, 'children', 1, 'children', 0])]: { x: 0, y: 0 }
    })
  })

  it('leaves the positions as they were for an edit that moves no node', () => {
    const positions = { [idOf(['parts', 0])]: at }

    expect(positionsAfterEdit(positions, { op: 'set', object: ['parts', 0], key: 'role', literal: '"a"' }, {})).toBe(positions)
  })
})
