import { IdeIcon } from '@/components/IdeIcon'
import { useEffect, useState } from 'react'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import { cn } from '@/lib/utils'
import type { BridgeClient } from '../bridge/BridgeClient'
import type { BakeRun } from '../bridge/types'
import { BAKE_RUN_COUNT, bakeWeightOf, formatMillis, measureBake, meterFill, runMillis, slowestStageIndex, weightScale } from '../scene/bakeTiming'

const MINIMUM_FILL = '4px'
const MEASURING_LABEL = 'Timing'
const NANOS_PER_MILLI = 1_000_000
const PERCENT = 100

/**
 * The chosen entry's bake timed {@link BAKE_RUN_COUNT} times in the background, kept as the median run; null while it is
 * timed. Opening another entry, or a pack reload, stops the runs under way.
 */
export function useBakeTiming(client: BridgeClient, viewId: string, key: string | null, baked: boolean, packRevision: number, tell: (message: string) => void): BakeRun | null {
  const [timing, setTiming] = useState<BakeRun | null>(null)

  useEffect(() => {
    setTiming(null)
    if (!baked || key === null) {
      return
    }
    const stop = new AbortController()
    measureBake(
      () => client.sceneBake(viewId, key, stop.signal),
      () => !stop.signal.aborted
    )
      .then((median) => median !== null && setTiming(median))
      .catch((failure: Error) => !stop.signal.aborted && tell(failure.message))
    return () => stop.abort()
  }, [client, viewId, key, baked, packRevision, tell])

  return timing
}

interface BakeMeterProps {
  /** The median bake of the chosen entry, or null while it is timed. */
  timing: BakeRun | null
}

/** How heavy the chosen entry's bake is for the chunk that first places it: a rating, a meter and the time, with each step's time in the tooltip. */
export function BakeMeter({ timing }: BakeMeterProps): React.JSX.Element {
  const millis = timing === null ? 0 : runMillis(timing)
  const weight = bakeWeightOf(millis)
  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <div className="flex h-8 shrink-0 items-center gap-2 border-b px-4 text-[11.5px]">
          <IdeIcon name="stopWatch" className={cn(timing === null && 'opacity-50')} />
          <span className={cn('w-[62px] shrink-0 font-semibold', timing === null ? 'text-muted-foreground' : weight.textClass)}>
            {timing === null ? MEASURING_LABEL : weight.label}
          </span>
          <div className="h-1 min-w-0 flex-1 rounded-full bg-muted">
            {timing !== null && <div className={cn('h-full rounded-full', weight.fillClass)} style={{ width: `${meterFill(millis) * PERCENT}%`, minWidth: MINIMUM_FILL }} />}
          </div>
          <span className="w-[46px] shrink-0 text-right text-secondary-text tabular-nums">{timing !== null && formatMillis(millis)}</span>
        </div>
      </TooltipTrigger>
      <TooltipContent side="left">{timing === null ? `Baking every variant ${BAKE_RUN_COUNT} times over` : <BakeExplanation timing={timing} />}</TooltipContent>
    </Tooltip>
  )
}

function BakeExplanation({ timing }: { timing: BakeRun }): React.JSX.Element {
  const slowest = slowestStageIndex(timing)
  const variants = timing.variantCount === 1 ? '1 variant' : `${timing.variantCount} variants`
  return (
    <div className="flex flex-col gap-1">
      <span>
        {variants} in {formatMillis(runMillis(timing))} · median of {BAKE_RUN_COUNT} runs
      </span>
      <div className="grid grid-cols-[auto_auto] gap-x-4 tabular-nums">
        {timing.stages.map((stage, index) => (
          <div key={stage.label} className={cn('col-span-2 grid grid-cols-subgrid', index === slowest && 'font-semibold')}>
            <span>{stage.label}</span>
            <span className="text-right">{formatMillis(stage.nanos / NANOS_PER_MILLI)}</span>
          </div>
        ))}
      </div>
      <span>Paid once per pack load, by the first chunk placing it</span>
      <span>{weightScale()}</span>
    </div>
  )
}
