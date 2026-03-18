import { useEffect, useMemo, useState } from 'react'
import { Box, Stack, Typography, TextField } from '@mui/material'

const SECTOR_SIZE = 1000 // meters (1km x 1km sector)
const GRID_STEP = 100 // 100m grid inside sector
const EPS_MOVE_METERS = 1e-6 // track every move; ignore exact duplicates

function clamp(n, min, max) {
  return Math.max(min, Math.min(max, n))
}

function compressByMovement(points, epsMoveMeters) {
  const out = []
  const eps2 = epsMoveMeters * epsMoveMeters
  let last = null
  for (const p of points) {
    if (typeof p?.x !== 'number' || typeof p?.y !== 'number') continue
    if (!last) {
      out.push(p)
      last = p
      continue
    }
    const dx = p.x - last.x
    const dy = p.y - last.y
    const dz = typeof p.z === 'number' && typeof last.z === 'number' ? p.z - last.z : 0
    // Nur exakte Duplikate ignorieren (Rotation/no-op); jede echte Bewegung behalten
    if (dx * dx + dy * dy + dz * dz > eps2) {
      out.push(p)
      last = p
    }
  }
  return out
}

function inBounds(p, b) {
  return p.x >= b.minX && p.x <= b.maxX && p.y >= b.minY && p.y <= b.maxY
}

function toSvg(p, b, w, h, pad) {
  const usableW = w - 2 * pad
  const usableH = h - 2 * pad
  const nx = (p.x - b.minX) / (b.maxX - b.minX)
  const ny = (p.y - b.minY) / (b.maxY - b.minY)
  const x = pad + nx * usableW
  const y = pad + (1 - ny) * usableH // North (higher y) at top
  return { x, y }
}

export default function SubmarineTrackMapSection({ apiGet, selectedSubId, setSelectedSubId, activeSubs }) {
  const [limit, setLimit] = useState(60)
  const [data, setData] = useState({ loading: false, error: null, positions: [], subId: null })

  const effectiveSubId = selectedSubId ?? (activeSubs?.[0]?.id ?? null)

  useEffect(() => {
    if (!effectiveSubId) {
      setData({ loading: false, error: null, positions: [], subId: null })
      return
    }

    let cancelled = false
    const safeLimit = clamp(Number(limit) || 60, 1, 500)

    const fetchOnce = async () => {
      try {
        if (!cancelled) setData((prev) => ({ ...prev, loading: true, error: null, subId: effectiveSubId }))
        const res = await apiGet(`/submarine/positions?id=${encodeURIComponent(effectiveSubId)}&limit=${safeLimit}`)
        const positions = Array.isArray(res.positions) ? res.positions : []
        if (!cancelled) {
          setData({
            loading: false,
            error: null,
            positions,
            subId: res.submarine_id ?? effectiveSubId,
          })
        }
      } catch (e) {
        if (!cancelled) setData((prev) => ({ ...prev, loading: false, error: e.message ?? String(e) }))
      }
    }

    fetchOnce()
    const id = setInterval(fetchOnce, 1000)
    return () => {
      cancelled = true
      clearInterval(id)
    }
  }, [apiGet, effectiveSubId, limit])

  const view = useMemo(() => {
    const raw = data.positions ?? []
    const moved = compressByMovement(raw, EPS_MOVE_METERS)
    const spawn = moved.length ? moved[0] : null
    const sectorMinX = spawn ? Math.floor(spawn.x / SECTOR_SIZE) * SECTOR_SIZE : 0
    const sectorMinY = spawn ? Math.floor(spawn.y / SECTOR_SIZE) * SECTOR_SIZE : 0
    const bounds = {
      minX: sectorMinX,
      maxX: sectorMinX + SECTOR_SIZE,
      minY: sectorMinY,
      maxY: sectorMinY + SECTOR_SIZE,
    }
    const inside = moved.filter((p) => inBounds(p, bounds))

    const w = 920
    const h = 560
    const pad = 44

    const dots = inside.map((p) => ({ ...toSvg(p, bounds, w, h, pad), raw: p }))
    const path = dots.map((d) => `${d.x.toFixed(2)},${d.y.toFixed(2)}`).join(' ')
    const latest = inside.length ? inside[inside.length - 1] : null

    return { w, h, pad, dots, path, rawCount: raw.length, movedCount: moved.length, latest, bounds, spawn }
  }, [data.positions])

  const subOptions = activeSubs?.length ? activeSubs : []

  return (
    <Box
      component="section"
      aria-label="Submarine Track Map"
      sx={{
        borderRadius: 3,
        p: 2,
        background: 'linear-gradient(145deg, rgba(15,23,42,0.97), rgba(15,23,42,0.9))',
      }}
    >
      <Stack spacing={2}>
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} alignItems={{ sm: 'center' }}>
          <Box sx={{ flex: 1 }}>
            <Typography variant="h6">Map (10×10 km)</Typography>
            <Typography variant="body2" color="text.secondary">
              Fixe Karte: North oben, East rechts. Es werden nur echte Bewegungen getrackt (keine Rotations).
            </Typography>
          </Box>

          <Stack direction="row" spacing={2} alignItems="center">
            {subOptions.length ? (
              <TextField
                size="small"
                select
                label="Sub"
                value={effectiveSubId ?? ''}
                onChange={(e) => setSelectedSubId?.(e.target.value)}
                SelectProps={{ native: true }}
                sx={{ width: 240 }}
              >
                {subOptions.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.id}
                  </option>
                ))}
              </TextField>
            ) : null}
            <TextField
              size="small"
              label="Letzte Punkte"
              value={limit}
              onChange={(e) => setLimit(clamp(parseInt(e.target.value || '0', 10) || 0, 1, 500))}
              inputProps={{ min: 1, max: 500 }}
              sx={{ width: 140 }}
            />
          </Stack>
        </Stack>

        {!effectiveSubId ? (
          <Typography variant="body2" color="text.secondary">
            Kein aktives Submarine.
          </Typography>
        ) : data.error ? (
          <Typography variant="body2" color="error">
            Fehler: {data.error}
          </Typography>
        ) : (
          <Box
            sx={{
              borderRadius: 2,
              border: '1px solid rgba(148,163,184,0.35)',
              overflow: 'hidden',
              background: 'rgba(2,6,23,0.55)',
            }}
          >
            <Box sx={{ px: 2, py: 1, borderBottom: '1px solid rgba(148,163,184,0.22)' }}>
              <Typography variant="caption" color="text.secondary" display="block">
                Sub: {data.subId ?? effectiveSubId} • DB: {view.rawCount} • Bewegung: {view.movedCount} •{' '}
                {data.loading ? 'lädt…' : 'ok'}
              </Typography>
              <Typography variant="caption" color="text.secondary" display="block">
                Sector: x=[{view.bounds.minX}..{view.bounds.maxX}]m, y=[{view.bounds.minY}..{view.bounds.maxY}]m • Latest:{' '}
                {view.latest ? `[${view.latest.x.toFixed(1)}, ${view.latest.y.toFixed(1)}]` : '—'}
              </Typography>
            </Box>

            <Box sx={{ width: '100%', overflowX: 'auto' }}>
              <svg
                width="100%"
                viewBox={`0 0 ${view.w} ${view.h}`}
                preserveAspectRatio="xMidYMid meet"
                style={{ display: 'block' }}
              >
                <defs>
                  <linearGradient id="trackStrokeClean" x1="0" y1="0" x2="1" y2="0">
                    <stop offset="0%" stopColor="rgba(56,189,248,0.25)" />
                    <stop offset="65%" stopColor="rgba(56,189,248,0.7)" />
                    <stop offset="100%" stopColor="rgba(34,197,94,0.9)" />
                  </linearGradient>
                </defs>

                {/* Outer frame */}
                <rect
                  x={view.pad}
                  y={view.pad}
                  width={view.w - 2 * view.pad}
                  height={view.h - 2 * view.pad}
                  fill="rgba(2,6,23,0.08)"
                  stroke="rgba(148,163,184,0.35)"
                  strokeWidth="1.2"
                />

                {/* Grid: equal 1km squares */}
                {(() => {
                  const lines = []
                  const usableW = view.w - 2 * view.pad
                  const usableH = view.h - 2 * view.pad
                  for (let m = 0; m <= SECTOR_SIZE; m += GRID_STEP) {
                    const t = m / SECTOR_SIZE
                    const x = view.pad + t * usableW
                    const y = view.pad + (1 - t) * usableH
                    const major = m % 500 === 0
                    const stroke = major ? 'rgba(148,163,184,0.22)' : 'rgba(148,163,184,0.12)'
                    const sw = major ? 1.2 : 1
                    lines.push(<line key={`vx-${m}`} x1={x} y1={view.pad} x2={x} y2={view.h - view.pad} stroke={stroke} strokeWidth={sw} />)
                    lines.push(<line key={`hy-${m}`} x1={view.pad} y1={y} x2={view.w - view.pad} y2={y} stroke={stroke} strokeWidth={sw} />)
                  }
                  return lines
                })()}

                {/* Cardinal directions */}
                <text x={view.w / 2} y={18} textAnchor="middle" fontSize="13" fill="rgba(226,232,240,0.85)">N</text>
                <text x={view.w / 2} y={view.h - 10} textAnchor="middle" fontSize="13" fill="rgba(226,232,240,0.85)">S</text>
                <text x={12} y={view.h / 2} textAnchor="start" fontSize="13" fill="rgba(226,232,240,0.85)">W</text>
                <text x={view.w - 12} y={view.h / 2} textAnchor="end" fontSize="13" fill="rgba(226,232,240,0.85)">E</text>

                {/* Track */}
                {view.path ? (
                  <polyline
                    points={view.path}
                    fill="none"
                    stroke="url(#trackStrokeClean)"
                    strokeWidth="3"
                    strokeLinejoin="round"
                    strokeLinecap="round"
                    opacity="0.95"
                  />
                ) : null}

                {view.dots.map((d, idx) => {
                  const isLast = idx === view.dots.length - 1
                  const r = isLast ? 6 : 3.2
                  const fill = isLast ? 'rgba(34,197,94,0.95)' : 'rgba(56,189,248,0.7)'
                  const stroke = isLast ? 'rgba(34,197,94,1)' : 'rgba(56,189,248,0.95)'
                  const label = `x=${d.raw.x}, y=${d.raw.y}, z=${d.raw.z}, at=${d.raw.recorded_at}`
                  return (
                    <circle key={idx} cx={d.x} cy={d.y} r={r} fill={fill} stroke={stroke} strokeWidth="1.2">
                      <title>{label}</title>
                    </circle>
                  )
                })}
              </svg>
            </Box>
          </Box>
        )}
      </Stack>
    </Box>
  )
}

