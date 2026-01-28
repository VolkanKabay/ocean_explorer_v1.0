import { useEffect, useState, useCallback } from 'react'
import {
  AppBar,
  Box,
  Button,
  CssBaseline,
  Divider,
  Grid,
  IconButton,
  List,
  ListItem,
  ListItemText,
  Paper,
  Stack,
  TextField,
  Toolbar,
  Tooltip,
  Typography,
  createTheme,
  ThemeProvider,
  Chip,
} from '@mui/material'
import {
  DirectionsBoat,
  Radar,
  PhotoCamera,
  TravelExplore,
  PlayArrow,
  ArrowUpward,
  ArrowDownward,
  ArrowBack,
  ArrowForward,
  Subtitles,
  RotateLeft,
  RotateRight,
  ClearAll,
  DeleteForever,
} from '@mui/icons-material'
import './App.css'

const API_BASE = 'http://localhost:8080/api'

async function apiPost(path, body) {
  const res = await fetch(`${API_BASE}${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: body ? JSON.stringify(body) : undefined,
  })
  if (!res.ok) {
    throw new Error(`HTTP ${res.status}`)
  }
  return res.json().catch(() => ({}))
}

async function apiGet(path) {
  const res = await fetch(`${API_BASE}${path}`)
  if (!res.ok) {
    throw new Error(`HTTP ${res.status}`)
  }
  return res.json()
}

const theme = createTheme({
  palette: {
    mode: 'dark',
    primary: {
      main: '#38bdf8',
    },
    secondary: {
      main: '#22c55e',
    },
    background: {
      default: 'transparent',
      paper: 'rgba(15,23,42,0.9)',
    },
  },
  shape: {
    borderRadius: 14,
  },
})

function App() {
  const [state, setState] = useState(null)
  const [logs, setLogs] = useState([])
  const [isLaunching, setIsLaunching] = useState(false)
  const [radarData, setRadarData] = useState({ echos: [], shipSector: null })
  const [launchParams, setLaunchParams] = useState({
    name: 'Explorer1',
    x: 0,
    y: 0,
    dx: 0,
    dy: 1,
  })

  const appendLog = useCallback((msg) => {
    setLogs((prev) => [
      ...prev.slice(-199),
      `[${new Date().toLocaleTimeString()}] ${msg}`,
    ])
  }, [])

  const refreshState = useCallback(async () => {
    try {
      const s = await apiGet('/state')
      setState(s)
    } catch (e) {
      appendLog(`Fehler beim Laden des Zustands: ${e.message}`)
    }
  }, [appendLog])

  useEffect(() => {
    refreshState()
    const id = setInterval(refreshState, 2000)
    return () => clearInterval(id)
  }, [refreshState])

  const handleLaunch = async () => {
    setIsLaunching(true)
    try {
      await apiPost('/launch', launchParams)
      appendLog('Launch-Befehl gesendet')
      await refreshState()
    } catch (e) {
      appendLog(`Launch fehlgeschlagen: ${e.message}`)
    } finally {
      setIsLaunching(false)
    }
  }

  const sendNavigate = async (rudder, course) => {
    try {
      await apiPost('/navigate', { rudder, course })
      appendLog(`Navigate: rudder=${rudder}, course=${course}`)
      await refreshState()
    } catch (e) {
      appendLog(`Navigate fehlgeschlagen: ${e.message}`)
    }
  }

  const sendScan = async () => {
    try {
      const res = await apiPost('/scan')
      appendLog(`Scan: depth=${res.depth}, stddev=${res.stddev}`)
      await refreshState()
    } catch (e) {
      appendLog(`Scan fehlgeschlagen: ${e.message}`)
    }
  }

  const sendRadar = async () => {
    try {
      const res = await apiPost('/radar')
      appendLog(`Radar: ${res?.echos?.length ?? 0} Echos`)
      if (res?.echos && state?.ship?.sector) {
        setRadarData({
          echos: res.echos,
          shipSector: { ...state.ship.sector },
        })
      }
      await refreshState()
    } catch (e) {
      appendLog(`Radar fehlgeschlagen: ${e.message}`)
    }
  }

  const startSubmarine = async () => {
    try {
      await apiPost('/submarine/start')
      appendLog('Submarine gestartet')
      await refreshState()
    } catch (e) {
      appendLog(`Submarine-Start fehlgeschlagen: ${e.message}`)
    }
  }

  const pilotSubmarine = async (subId, route, action = '') => {
    try {
      await apiPost('/submarine/pilot', { id: subId, route, action })
      appendLog(`Pilot: id=${subId}, route=${route}, action=${action}`)
    } catch (e) {
      appendLog(`Pilot fehlgeschlagen: ${e.message}`)
    }
  }

  const killSubmarine = async (subId) => {
    try {
      await apiPost('/submarine/kill', { id: subId })
      appendLog(`Submarine gekillt: ${subId}`)
      await refreshState()
    } catch (e) {
      appendLog(`Kill fehlgeschlagen: ${e.message}`)
    }
  }

  const activeSubs = state?.submarines ?? []

  // Tastatursteuerung:
  // - WASD + Q/E: steuern immer das Schiff (Navigate)
  // - Pfeiltasten: steuern (falls vorhanden) das erste Submarine
  useEffect(() => {
    const handler = (e) => {
      if (e.target.tagName === 'INPUT') return
      const key = e.key.toLowerCase()
      const hasSub = activeSubs.length > 0

      // Schiff steuern mit WASD + Q/E
      if (key === 'w') {
        sendNavigate('Center', 'Forward')
      } else if (key === 's') {
        sendNavigate('Center', 'Backward')
      } else if (key === 'a') {
        sendNavigate('Left', 'Forward')
      } else if (key === 'd') {
        sendNavigate('Right', 'Forward')
      } else if (key === 'q') {
        sendNavigate('Left', 'Backward')
      } else if (key === 'e') {
        sendNavigate('Right', 'Backward')
      }

      // Submarine mit Pfeiltasten (falls vorhanden)
      if (!hasSub) return

      if (e.key === 'ArrowUp') {
        // Geradeaus fahren
        pilotSubmarine(undefined, 'C')
      } else if (e.key === 'ArrowDown') {
        // Abtauchen
        pilotSubmarine(undefined, 'DOWN')
      } else if (e.key === 'ArrowLeft') {
        // Nach links drehen
        pilotSubmarine(undefined, 'W')
      } else if (e.key === 'ArrowRight') {
        // Nach rechts drehen
        pilotSubmarine(undefined, 'E')
      }
    }
    window.addEventListener('keydown', handler)
    return () => window.removeEventListener('keydown', handler)
  }, [activeSubs, pilotSubmarine, sendNavigate])

  const clearAll = () => {
    setLogs([])
    setRadarData({ echos: [], shipSector: null })
  }

  const resetSession = async () => {
    try {
      await apiPost('/reset')
      appendLog('Session reset (Ship & Submarines zurückgesetzt)')
      setState(null)
      setRadarData({ echos: [], shipSector: null })
    } catch (e) {
      appendLog(`Reset fehlgeschlagen: ${e.message}`)
    }
  }

  const renderRadarBlips = () => {
    if (!radarData.echos || !radarData.shipSector) return null

    const cx = 110
    const cy = 110
    const radius = 80

    return radarData.echos
      .filter((echo) => typeof echo.height === 'number' && echo.height > 0)
      .map((echo, idx) => {
        const sec = echo.sector?.vec2
        if (!sec || sec.length !== 2) return null
        const [sx, sy] = sec
        const dx = sx - radarData.shipSector.x
        const dy = sy - radarData.shipSector.y

        if (dx === 0 && dy === 0) return null

        const angle = Math.atan2(-dy, dx)
        const r = radius * 0.9
        const x = cx + r * Math.cos(angle)
        const y = cy + r * Math.sin(angle)

        return (
          <circle
            key={`${sx}-${sy}-${idx}`}
            cx={x}
            cy={y}
            r={5}
            fill="#f97316"
            stroke="#fed7aa"
            strokeWidth={1}
          />
        )
      })
  }

  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <Box sx={{ minHeight: '100vh', display: 'flex', flexDirection: 'column' }}>
        <AppBar
          position="static"
          color="transparent"
          enableColorOnDark
          sx={{
            backdropFilter: 'blur(16px)',
            background:
              'linear-gradient(to right, rgba(15,23,42,0.95), rgba(8,47,73,0.85))',
            borderBottom: '1px solid rgba(148,163,184,0.4)',
          }}
        >
          <Toolbar>
            <DirectionsBoat sx={{ mr: 1, color: '#38bdf8' }} />
            <Typography variant="h6" sx={{ flexGrow: 1 }}>
              Ocean Explorer – ShipApp Control
            </Typography>
            {state?.ship && (
              <Stack direction="row" spacing={1} alignItems="center">
                <Chip
                  color="primary"
                  size="small"
                  label={`ShipID: ${state.ship.id}`}
                  icon={<Subtitles />}
                  variant="outlined"
                />
              </Stack>
            )}
          </Toolbar>
        </AppBar>

        <Box
          sx={{
            flex: 1,
            p: 2,
            display: 'flex',
            alignItems: 'stretch',
          }}
        >
          <Grid
            container
            spacing={2}
            sx={{ maxWidth: 1400, margin: '0 auto' }}
          >
            {/* Schiff starten */}
            <Grid item xs={12} md={4}>
              <Paper
                elevation={6}
                sx={{
                  p: 2,
                  height: '100%',
                  borderRadius: 3,
                  position: 'relative',
                  overflow: 'hidden',
                }}
              >
                <Box
                  sx={{
                    position: 'absolute',
                    inset: 0,
                    pointerEvents: 'none',
                    background:
                      'radial-gradient(circle at top left, rgba(56,189,248,0.2), transparent 60%)',
                  }}
                />
                <Stack spacing={2} sx={{ position: 'relative' }}>
                  <Typography variant="h6">Schiff starten</Typography>
                  <Typography variant="body2" color="text.secondary">
                    Verbinde dein Forschungsschiff mit dem Ocean-Server und
                    setze die Startposition.
                  </Typography>
                  <Grid container spacing={1}>
                    <Grid item xs={12}>
                      <TextField
                        label="Name"
                        size="small"
                        fullWidth
                        value={launchParams.name}
                        onChange={(e) =>
                          setLaunchParams({
                            ...launchParams,
                            name: e.target.value,
                          })
                        }
                      />
                    </Grid>
                    <Grid item xs={6}>
                      <TextField
                        label="Sektor X"
                        size="small"
                        type="number"
                        fullWidth
                        value={launchParams.x}
                        onChange={(e) =>
                          setLaunchParams({
                            ...launchParams,
                            x: Number(e.target.value),
                          })
                        }
                      />
                    </Grid>
                    <Grid item xs={6}>
                      <TextField
                        label="Sektor Y"
                        size="small"
                        type="number"
                        fullWidth
                        value={launchParams.y}
                        onChange={(e) =>
                          setLaunchParams({
                            ...launchParams,
                            y: Number(e.target.value),
                          })
                        }
                      />
                    </Grid>
                    <Grid item xs={6}>
                      <TextField
                        label="Richtung dx"
                        size="small"
                        type="number"
                        fullWidth
                        value={launchParams.dx}
                        onChange={(e) =>
                          setLaunchParams({
                            ...launchParams,
                            dx: Number(e.target.value),
                          })
                        }
                      />
                    </Grid>
                    <Grid item xs={6}>
                      <TextField
                        label="Richtung dy"
                        size="small"
                        type="number"
                        fullWidth
                        value={launchParams.dy}
                        onChange={(e) =>
                          setLaunchParams({
                            ...launchParams,
                            dy: Number(e.target.value),
                          })
                        }
                      />
                    </Grid>
                  </Grid>
                  <Stack direction="row" spacing={1}>
                    <Button
                      variant="contained"
                      color="primary"
                      startIcon={<PlayArrow />}
                      onClick={handleLaunch}
                      disabled={isLaunching}
                    >
                      {isLaunching ? 'Launching…' : 'Schiff launchen'}
                    </Button>
                    <Button
                      variant="outlined"
                      color="inherit"
                      onClick={resetSession}
                    >
                      Reset Session
                    </Button>
                  </Stack>
                  {state?.ship && (
                    <>
                      <Divider sx={{ my: 1.5 }} />
                      <Stack spacing={0.5}>
                        <Typography variant="subtitle2">
                          Aktueller Zustand
                        </Typography>
                        <Typography variant="body2">
                          Sektor: [{state.ship.sector?.x},{' '}
                          {state.ship.sector?.y}]
                        </Typography>
                        <Typography variant="body2">
                          Richtung: [{state.ship.dir?.x},{' '}
                          {state.ship.dir?.y}]
                        </Typography>
                      </Stack>
                    </>
                  )}
                </Stack>
              </Paper>
            </Grid>

            {/* Navigation */}
            <Grid item xs={12} md={4}>
              <Paper
                elevation={6}
                sx={{
                  p: 2,
                  height: '100%',
                  borderRadius: 3,
                }}
              >
                <Stack spacing={2} alignItems="stretch">
                  <Typography variant="h6">Navigation</Typography>
                  <Typography variant="body2" color="text.secondary">
                    Nutze die Buttons oder die Tastatur (WASD + Q/E), um das
                    Schiff im 10×10 km Ozean zu bewegen.
                  </Typography>
                  <Grid container spacing={2}>
                    <Grid item xs={12} sm={6}>
                      <Box
                        sx={{
                          display: 'grid',
                          gridTemplateColumns: 'repeat(3, 1fr)',
                          gap: 1,
                          justifyItems: 'center',
                        }}
                      >
                        <Tooltip title="Vorwärts links (A)">
                          <span>
                            <IconButton
                              color="primary"
                              onClick={() =>
                                sendNavigate('Left', 'Forward')
                              }
                            >
                              <ArrowBack />
                            </IconButton>
                          </span>
                        </Tooltip>
                        <Tooltip title="Vorwärts (W)">
                          <span>
                            <IconButton
                              color="primary"
                              onClick={() =>
                                sendNavigate('Center', 'Forward')
                              }
                            >
                              <ArrowUpward />
                            </IconButton>
                          </span>
                        </Tooltip>
                        <Tooltip title="Vorwärts rechts (D)">
                          <span>
                            <IconButton
                              color="primary"
                              onClick={() =>
                                sendNavigate('Right', 'Forward')
                              }
                            >
                              <ArrowForward />
                            </IconButton>
                          </span>
                        </Tooltip>
                        <Tooltip title="Rückwärts links (Q)">
                          <span>
                            <IconButton
                              color="primary"
                              onClick={() =>
                                sendNavigate('Left', 'Backward')
                              }
                            >
                              <ArrowBack />
                            </IconButton>
                          </span>
                        </Tooltip>
                        <Tooltip title="Rückwärts (S)">
                          <span>
                            <IconButton
                              color="primary"
                              onClick={() =>
                                sendNavigate('Center', 'Backward')
                              }
                            >
                              <ArrowDownward />
                            </IconButton>
                          </span>
                        </Tooltip>
                        <Tooltip title="Rückwärts rechts (E)">
                          <span>
                            <IconButton
                              color="primary"
                              onClick={() =>
                                sendNavigate('Right', 'Backward')
                              }
                            >
                              <ArrowForward />
                            </IconButton>
                          </span>
                        </Tooltip>
                      </Box>
                      <Stack direction="row" spacing={1} sx={{ mt: 2 }}>
                        <Button
                          variant="outlined"
                          startIcon={<TravelExplore />}
                          onClick={sendScan}
                        >
                          Scan
                        </Button>
                        <Button
                          variant="outlined"
                          startIcon={<Radar />}
                          onClick={sendRadar}
                        >
                          Radar
                        </Button>
                      </Stack>
                    </Grid>
                    <Grid item xs={12} sm={6}>
                  
                      <Box
                        sx={{
                          position: 'relative',
                          width: 220,
                          height: 220,
                          borderRadius: '50%',
                          overflow: 'hidden',
                          boxShadow:
                            '0 0 25px rgba(56,189,248,0.6), inset 0 0 20px rgba(15,23,42,0.9)',
                          backgroundColor: '#020617',
                        }}
                      >
                        <img
                          src="/radar.gif"
                          alt="Radar"
                          style={{
                            width: '100%',
                            height: '100%',
                            objectFit: 'cover',
                            mixBlendMode: 'screen',
                            opacity: 0.9,
                          }}
                        />
                        <svg
                          width={220}
                          height={220}
                          style={{
                            position: 'absolute',
                            inset: 0,
                            pointerEvents: 'none',
                          }}
                        >
                          {renderRadarBlips()}
                        </svg>
                      </Box>
                    </Grid>
                  </Grid>
                </Stack>
              </Paper>
            </Grid>

            {/* Submarines & Logs */}
            <Grid item xs={12} md={4}>
              <Paper
                elevation={6}
                sx={{
                  p: 2,
                  mb: 2,
                  borderRadius: 3,
                }}
              >
                <Stack spacing={2}>
                  <Typography variant="h6">Submarines</Typography>
                  <Typography variant="body2" color="text.secondary">
                    Setze Tauchroboter ein, um Details des aktuellen Sektors zu
                    erforschen. Während des Tauchgangs darf das Schiff den
                    Sektor nicht wechseln.
                  </Typography>
                  <Button
                    variant="contained"
                    color="secondary"
                    startIcon={<DirectionsBoat />}
                    onClick={startSubmarine}
                  >
                    Submarine starten
                  </Button>
                  {activeSubs.length === 0 ? (
                    <Typography variant="body2" color="text.secondary">
                      Keine aktiven Submarines verbunden.
                    </Typography>
                  ) : (
                    <List dense>
                      {activeSubs.map((s) => (
                        <ListItem
                          key={s.id}
                          disableGutters
                          secondaryAction={
                            <Stack direction="row" spacing={0.5}>
                              <Tooltip title="Geradeaus">
                                <IconButton
                                  size="small"
                                  onClick={() => pilotSubmarine(s.id, 'C')}
                                >
                                  <PlayArrow fontSize="small" />
                                </IconButton>
                              </Tooltip>
                              <Tooltip title="Aufsteigen">
                                <IconButton
                                  size="small"
                                  onClick={() =>
                                    pilotSubmarine(s.id, 'UP')
                                  }
                                >
                                  <ArrowUpward fontSize="small" />
                                </IconButton>
                              </Tooltip>
                              <Tooltip title="Abtauchen">
                                <IconButton
                                  size="small"
                                  onClick={() =>
                                    pilotSubmarine(s.id, 'DOWN')
                                  }
                                >
                                  <ArrowDownward fontSize="small" />
                                </IconButton>
                              </Tooltip>
                              <Tooltip title="Foto">
                                <IconButton
                                  size="small"
                                  onClick={() =>
                                    pilotSubmarine(
                                      s.id,
                                      'None',
                                      'take_photo',
                                    )
                                  }
                                >
                                  <PhotoCamera fontSize="small" />
                                </IconButton>
                              </Tooltip>
                              <Tooltip title="Locate">
                                <IconButton
                                  size="small"
                                  onClick={() =>
                                    pilotSubmarine(s.id, 'None', 'locate')
                                  }
                                >
                                  <TravelExplore fontSize="small" />
                                </IconButton>
                              </Tooltip>
                              <Tooltip title="Rotate left">
                                <IconButton
                                  size="small"
                                  onClick={() =>
                                    pilotSubmarine(s.id, 'W')
                                  }
                                >
                                  <RotateLeft fontSize="small" />
                                </IconButton>
                              </Tooltip>
                              <Tooltip title="Rotate right">
                                <IconButton
                                  size="small"
                                  onClick={() =>
                                    pilotSubmarine(s.id, 'E')
                                  }
                                >
                                  <RotateRight fontSize="small" />
                                </IconButton>
                              </Tooltip>
                              <Tooltip title="Kill submarine">
                                <IconButton
                                  size="small"
                                  color="error"
                                  onClick={() => killSubmarine(s.id)}
                                >
                                  <DeleteForever fontSize="small" />
                                </IconButton>
                              </Tooltip>
                            </Stack>
                          }
                        >
                          <ListItemText
                            primary={s.id}
                            secondary={`pos [${s.pos?.x ?? '?'},${
                              s.pos?.y ?? '?'
                            },${s.pos?.z ?? '?'}], depth ${
                              s.depth ?? '?'
                            }m, dist ${s.distance ?? '?'}m`}
                          />
                        </ListItem>
                      ))}
                    </List>
                  )}
                </Stack>
              </Paper>

              <Paper
                elevation={6}
                sx={{
                  p: 2,
                  borderRadius: 3,
                  maxHeight: 260,
                  display: 'flex',
                  flexDirection: 'column',
                }}
              >
                <Stack
                  direction="row"
                  alignItems="center"
                  justifyContent="space-between"
                >
                  <Typography variant="h6">Log</Typography>
                  <Button
                    size="small"
                    color="inherit"
                    startIcon={<ClearAll fontSize="small" />}
                    onClick={clearAll}
                  >
                    Clear All
                  </Button>
                </Stack>
                <Box
                  sx={{
                    mt: 1,
                    flex: 1,
                    overflowY: 'auto',
                    fontFamily: 'JetBrains Mono, ui-monospace, monospace',
                    fontSize: 12,
                    pr: 1,
                  }}
                >
                  {logs.map((l, i) => (
                    <Typography key={i} variant="body2">
                      {l}
                    </Typography>
                  ))}
                </Box>
              </Paper>
            </Grid>
          </Grid>
        </Box>
      </Box>
    </ThemeProvider>
  )
}

export default App
