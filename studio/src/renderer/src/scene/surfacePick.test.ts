import { Box3, BoxGeometry, Group, Mesh, MeshBasicMaterial, Ray, Vector3 } from 'three'
import { describe, expect, it } from 'vitest'
import type { ViewportContent } from '../studio/SceneViewport'
import { surfaceAlong } from './surfacePick'

const REACH = 100

function boxContent(): ViewportContent {
  const object = new Group()
  object.add(new Mesh(new BoxGeometry(2, 2, 2), new MeshBasicMaterial()))
  object.updateMatrixWorld(true)
  return { object, bounds: new Box3(new Vector3(-1, -1, -1), new Vector3(1, 1, 1)) }
}

function lookingDownTheDepth(x: number): Ray {
  return new Ray(new Vector3(x, 0, 10), new Vector3(0, 0, -1))
}

describe('surface picking', () => {
  it('finds the nearest face of the drawn meshes along the ray', () => {
    expect(surfaceAlong(boxContent(), lookingDownTheDepth(0), REACH)!.toArray()).toEqual([0, 0, 1])
  })

  it('finds nothing where the ray passes by or the surface lies beyond its reach', () => {
    expect(surfaceAlong(boxContent(), lookingDownTheDepth(5), REACH)).toBeNull()
    expect(surfaceAlong(boxContent(), lookingDownTheDepth(0), 5)).toBeNull()
  })

  it('asks the content when it knows its own surface', () => {
    const known = new Vector3(1, 2, 3)
    const content: ViewportContent = { ...boxContent(), surfaceAlong: () => known }
    expect(surfaceAlong(content, lookingDownTheDepth(5), REACH)).toBe(known)
  })
})
