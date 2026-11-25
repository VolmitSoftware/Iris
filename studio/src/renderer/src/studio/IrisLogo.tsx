import { useId } from 'react'
import { cn } from '@/lib/utils'

const HEXAGON = 'M12 5 18.06 8.5V15.5L12 19 5.94 15.5V8.5Z'
// Each bracket follows two edges of the hexagon two units out, as the Iris logo has; the second is the first turned half way.
const UPPER_BRACKET = 'M12.2 2.57 3.94 7.35V10.2'
const LOWER_BRACKET = 'M11.8 21.43 20.06 16.65V13.8'

/** The Iris logo: its hexagon in the logo's teal and its brackets in the theme's icon colour, so both themes show them. */
export function IrisLogo({ className }: { className?: string }): React.JSX.Element {
  const gradient = useId()
  return (
    <svg viewBox="0 0 24 24" aria-label="Iris" role="img" className={cn('glyph shrink-0', className)}>
      <defs>
        <linearGradient id={gradient} x1="0" y1="0" x2="0" y2="1">
          <stop offset="0" stopColor="#22c08b" />
          <stop offset="1" stopColor="#2b8bc2" />
        </linearGradient>
      </defs>
      <path d={HEXAGON} fill={`url(#${gradient})`} />
      <path d={UPPER_BRACKET} fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinejoin="miter" />
      <path d={LOWER_BRACKET} fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinejoin="miter" />
    </svg>
  )
}
