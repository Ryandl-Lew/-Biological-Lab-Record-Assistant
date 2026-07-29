import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import DataChart, { chartFromFit } from './DataChart'

describe('DataChart', () => {
  it('renders bar chart from series points', () => {
    render(
      <DataChart
        type="bar"
        title="测试柱状图"
        xLabel="浓度"
        yLabel="Ct"
        series={[{ name: 'Ct', points: [{ x: 1, y: 2, label: 'A' }, { x: 2, y: 4, label: 'B' }] }]}
      />,
    )
    expect(screen.getByRole('img', { name: '测试柱状图' })).toBeInTheDocument()
    expect(screen.getByText('测试柱状图')).toBeInTheDocument()
  })

  it('builds scatter+curve chart from fit payload', () => {
    const chart = chartFromFit({
      equation: 'y=a+b*x',
      points: [{ x: 1, y: 2, recordCode: 'R1' }],
      curveSample: [{ x: 1, y: 2.1 }, { x: 2, y: 3.1 }],
    })
    expect(chart.type).toBe('scatter')
    expect(chart.series).toHaveLength(2)
    render(<DataChart {...chart} />)
    expect(screen.getByRole('img', { name: '拟合图：y=a+b*x' })).toBeInTheDocument()
  })
})
