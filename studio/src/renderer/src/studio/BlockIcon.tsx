import { useEffect, useRef, useState } from 'react'
import type { BlockColors } from '../scene/blockColors'
import { cn } from '@/lib/utils'

/** The block's top texture as Minecraft draws it, 16 px and pixel-sharp; an empty square while it loads or when it has none. */
export function BlockIcon({ block, blocks, className }: { block: string; blocks: BlockColors; className?: string }): React.JSX.Element {
  const canvasRef = useRef<HTMLCanvasElement>(null)
  const [missing, setMissing] = useState(true)

  useEffect(() => {
    let active = true
    setMissing(true)
    void blocks.topTexture(block).then((texture) => {
      const canvas = canvasRef.current
      if (!active || texture === null || canvas === null) {
        return
      }
      canvas.width = texture.width
      canvas.height = texture.height
      canvas.getContext('2d')!.putImageData(texture, 0, 0)
      setMissing(false)
    })
    return () => {
      active = false
    }
  }, [block, blocks])

  return (
    <span className={cn('relative size-4 shrink-0', className)}>
      <canvas ref={canvasRef} className={cn('size-full [image-rendering:pixelated]', missing && 'invisible')} />
      {missing && <span className="absolute inset-0 rounded-[2px] border border-foreground/25" />}
    </span>
  )
}
