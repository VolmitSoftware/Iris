import { LucideProvider } from 'lucide-react'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { TooltipProvider } from '@/components/ui/tooltip'
import { App } from './App'
import './styles.css'

const ICON_SIZE_PX = 16
const ICON_STROKE_PX = 1.5

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <LucideProvider size={ICON_SIZE_PX} strokeWidth={ICON_STROKE_PX} nonScalingStroke className="shrink-0">
      <TooltipProvider delayDuration={400}>
        <App />
      </TooltipProvider>
    </LucideProvider>
  </StrictMode>
)
