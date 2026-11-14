import { parseState } from './blockModels'
import { INTS_PER_BLOCK, type BlockScene } from './sceneDecoding'

const PERSISTENT_PROPERTY = 'persistent'
const DISTANCE_PROPERTY = 'distance'

/** The distance vanilla gives a leaf with no wood near enough to keep it; IrisProceduralBlocks.UNREACHED_LEAF_DISTANCE. */
const UNREACHED_LEAF_DISTANCE = '7'

/** Whether a block state is a leaf baked with no wood near enough for vanilla to keep it; the rule of IrisProceduralBlocks.isLeafTooFarFromWood. */
export function isFarLeaf(state: string): boolean {
  const { properties } = parseState(state)
  return PERSISTENT_PROPERTY in properties && properties[DISTANCE_PROPERTY] === UNREACHED_LEAF_DISTANCE
}

/** How many blocks of the scene are leaves too far from wood. */
export function countFarLeaves(scene: BlockScene): number {
  const far = scene.palette.map(isFarLeaf)
  let count = 0
  for (let offset = 3; offset < scene.blocks.length; offset += INTS_PER_BLOCK) {
    if (far[scene.blocks[offset]]) {
      count++
    }
  }
  return count
}
