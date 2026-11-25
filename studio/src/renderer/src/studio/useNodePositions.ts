import { useEffect, useState } from 'react'
import type { Point } from '../editor/nodeLayout'
import type { NodePositions } from '../editor/nodePositions'

const STORAGE_PREFIX = 'iris-studio.node-positions:'

function isPoint(value: unknown): value is Point {
  return typeof value === 'object' && value !== null && typeof (value as Point).x === 'number' && typeof (value as Point).y === 'number'
}

// Unreadable positions just mean every node sits where the layout puts it.
function storedPositions(file: string): NodePositions {
  try {
    const stored: unknown = JSON.parse(localStorage.getItem(STORAGE_PREFIX + file) ?? '{}')
    return Object.fromEntries(Object.entries(stored as Record<string, unknown>).filter(([, position]) => isPoint(position))) as NodePositions
  } catch {
    return {}
  }
}

/** Where the nodes of {@code file} were dragged to, kept by the studio so the file only holds settings. */
export function useNodePositions(file: string): [NodePositions, React.Dispatch<React.SetStateAction<NodePositions>>] {
  const [positions, setPositions] = useState<NodePositions>(() => storedPositions(file))

  useEffect(() => {
    if (Object.keys(positions).length === 0) {
      localStorage.removeItem(STORAGE_PREFIX + file)
    } else {
      localStorage.setItem(STORAGE_PREFIX + file, JSON.stringify(positions))
    }
  }, [file, positions])

  return [positions, setPositions]
}
