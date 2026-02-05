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
  Visibility,
  Refresh,
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
  const [launchParams, setLaunchParams] = useState({
    name: 'Explorer1',
    x: 1,
    y: 1,
    dx: 0,
    dy: 0,
  })

  // Live-View State
  const [liveViewData, setLiveViewData] = useState({
    picture: null,
    id: null,
    timestamp: null,
    loading: false,
  })
  const [autoRefreshLiveView, setAutoRefreshLiveView] = useState(false)
  const [selectedSubId, setSelectedSubId] = useState(null)

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

  const startSubmarine = async () => {
    try {
      await apiPost('/submarine/start')
      appendLog('Submarine gestartet')
      await refreshState()
    } catch (e) {
      appendLog(`Submarine-Start fehlgeschlagen: ${e.message}`)
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

  // Live-View: Bild eines Submarines laden
  const fetchLiveView = useCallback(async (subId) => {
    setLiveViewData((prev) => ({ ...prev, loading: true }))
    try {
      const url = subId ? `/submarine/picture?id=${encodeURIComponent(subId)}` : '/submarine/picture'
      const res = await apiGet(url)
      if (res.picture && res.hasPicture) {
        setLiveViewData({
          picture: `data:image/png;base64,${res.picture}`,
          id: res.id,
          timestamp: res.timestamp,
          loading: false,
        })
      } else {
        setLiveViewData({
          picture: null,
          id: res.id || null,
          timestamp: null,
          loading: false,
        })
      }
    } catch (e) {
      appendLog(`Live-View fehlgeschlagen: ${e.message}`)
      setLiveViewData((prev) => ({ ...prev, loading: false }))
    }
  }, [appendLog])

  // Submarine steuern - bei jeder Bewegung automatisch Foto machen
  const pilotSubmarine = useCallback(async (subId, route, action = '') => {
    try {
      await apiPost('/submarine/pilot', { id: subId, route, action })
      appendLog(`Pilot: id=${subId}, route=${route}, action=${action}`)
      
      // Bei Bewegungen automatisch ein Foto machen (außer bei explizitem take_photo oder locate)
      const isMovement = route !== 'None' && action !== 'take_photo' && action !== 'locate'
      
      if (isMovement) {
        // Kurz warten bis Bewegung abgeschlossen, dann Foto
        setTimeout(async () => {
          try {
            await apiPost('/submarine/pilot', { id: subId, route: 'None', action: 'take_photo' })
            // Bild laden nach kurzem Delay
            setTimeout(() => fetchLiveView(subId), 600)
          } catch (e) {
            // Foto-Fehler ignorieren
          }
        }, 200)
      } else if (action === 'take_photo') {
        // Bei manuellem Foto auch Bild laden
        setTimeout(() => fetchLiveView(subId), 800)
      }
    } catch (e) {
      appendLog(`Pilot fehlgeschlagen: ${e.message}`)
    }
  }, [appendLog, fetchLiveView])

  // Auto-Refresh für Live-View
  useEffect(() => {
    if (!autoRefreshLiveView || !selectedSubId) return
    const id = setInterval(() => {
      fetchLiveView(selectedSubId)
    }, 3000)
    return () => clearInterval(id)
  }, [autoRefreshLiveView, selectedSubId, fetchLiveView])

  const activeSubs = state?.submarines ?? []

  // Wenn die aktuelle Auswahl nicht mehr existiert, auf erstes Sub umschalten oder auf null
  useEffect(() => {
    if (!activeSubs || activeSubs.length === 0) {
      setSelectedSubId(null)
      return
    }
    const stillExists = activeSubs.some((s) => s.id === selectedSubId)
    if (!stillExists) {
      setSelectedSubId(activeSubs[0].id)
    }
  }, [activeSubs, selectedSubId])

  // Tastatursteuerung:
  // - WASD + Q/E: steuern immer das Schiff (Navigate)
  // - Pfeiltasten: steuern (falls vorhanden) das erste Submarine
  useEffect(() => {
    const handler = (e) => {
      if (e.target.tagName === 'INPUT') return
      const key = e.key.toLowerCase()
      const hasSub = activeSubs.length > 0

      // Schiff steuern mit WASD + Q/E
      // W = vorwärts, A = vorwärts-links, D = vorwärts-rechts, S = rückwärts
      if (key === 'w') {
        sendNavigate('Center', 'Forward')
      } else if (key === 's') {
        sendNavigate('Center', 'Backward')
      } else if (key === 'q') {
        sendNavigate('Left', 'Backward')
      } else if (key === 'e') {
        sendNavigate('Right', 'Backward')
      } else if (key === 'd') {
        sendNavigate('Right', 'Forward')
      } else if (key === 'a') {
        sendNavigate('Left', 'Forward')
      }

      // Submarine mit Pfeiltasten (falls vorhanden)
      if (!hasSub) return

      const targetId =
        selectedSubId && activeSubs.some((s) => s.id === selectedSubId)
          ? selectedSubId
          : activeSubs[0].id

      if (e.key === 'ArrowUp') {
        // Geradeaus fahren
        pilotSubmarine(targetId, 'C')
      } else if (e.key === 'ArrowDown') {
        // Abtauchen
        pilotSubmarine(targetId, 'DOWN')
      } else if (e.key === 'ArrowLeft') {
        // Nach links drehen
        pilotSubmarine(targetId, 'W')
      } else if (e.key === 'ArrowRight') {
        // Nach rechts drehen
        pilotSubmarine(targetId, 'E')
      }
    }
    window.addEventListener('keydown', handler)
    return () => window.removeEventListener('keydown', handler)
  }, [activeSubs, pilotSubmarine, sendNavigate, selectedSubId])

  const clearAll = () => {
    setLogs([])
  }

  const resetSession = async () => {
    try {
      await apiPost('/reset')
      appendLog('Session reset (Ship & Submarines zurückgesetzt)')
      setState(null)
    } catch (e) {
      appendLog(`Reset fehlgeschlagen: ${e.message}`)
    }
  }

  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <Box
        sx={{
          minHeight: '100vh',
          display: 'flex',
          flexDirection: 'column',
          background:
            'radial-gradient(circle at top, #0f172a 0, #020617 45%, #000 100%)',
        }}
      >
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
            py: 3,
            px: 2,
            display: 'flex',
            alignItems: 'stretch',
            justifyContent: 'center',
          }}
        >
          <Paper
            elevation={10}
            sx={{
              width: '100%',
              maxWidth: 1320,
              borderRadius: 4,
              p: { xs: 2, sm: 3 },
              border: '1px solid rgba(148,163,184,0.35)',
              background:
                'radial-gradient(ellipse at top left, rgba(56,189,248,0.18), transparent 55%), radial-gradient(ellipse at bottom right, rgba(34,197,94,0.15), transparent 60%), rgba(15,23,42,0.98)',
            }}
          >
            <Stack spacing={3}>
              {/* Schiff starten */}
              <Box
                component="section"
                aria-labelledby="ship-launch-heading"
                sx={{
                  position: 'relative',
                  overflow: 'hidden',
                  borderRadius: 3,
                  p: 2,
                  background:
                    'linear-gradient(145deg, rgba(15,23,42,0.95), rgba(15,23,42,0.8))',
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
                  <Typography id="ship-launch-heading" variant="h6">
                    Schiff starten
                  </Typography>
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
                        defaultValue={1}
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
                                                defaultValue={1}

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
                  <Stack
                    direction={{ xs: 'column', sm: 'row' }}
                    spacing={1}
                    sx={{ alignItems: { xs: 'stretch', sm: 'center' } }}
                  >
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
              </Box>

              {/* Navigation */}
              <Box
                component="section"
                aria-labelledby="navigation-heading"
                sx={{
                  borderRadius: 3,
                  p: 2,
                  background:
                    'linear-gradient(145deg, rgba(15,23,42,0.95), rgba(15,23,42,0.8))',
                }}
              >
                <Stack spacing={2} alignItems="stretch">
                  <Typography id="navigation-heading" variant="h6">
                    Navigation
                  </Typography>
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
                        aria-label="Steuerkreuz für das Schiff"
                        role="group"
                      >
                        <Tooltip title="Vorwärts links (A)">
                          <span>
                            <IconButton
                              color="primary"
                              onClick={() =>
                                sendNavigate('Left', 'Forward')
                              }
                              aria-label="Vorwärts links (A)"
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
                              aria-label="Vorwärts (W)"
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
                              aria-label="Vorwärts rechts (D)"
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
                              aria-label="Rückwärts links (Q)"
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
                              aria-label="Rückwärts (S)"
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
                              aria-label="Rückwärts rechts (E)"
                            >
                              <ArrowForward />
                            </IconButton>
                          </span>
                        </Tooltip>
                      </Box>
                      <Stack
                        direction="row"
                        spacing={1}
                        sx={{ mt: 2, flexWrap: 'wrap' }}
                      >
                        <Button
                          variant="outlined"
                          startIcon={<TravelExplore />}
                          onClick={sendScan}
                        >
                          Scan
                        </Button>
                      </Stack>
                    </Grid>
                    <Grid item xs={12} sm={6}>
                      <Typography
                        variant="body2"
                        color="text.secondary"
                        sx={{ mt: { xs: 1, sm: 0 } }}
                      >
                        Tipp: Halte die Hand auf WASD und den Pfeiltasten, um
                        Schiff und Submarines parallel zu steuern.
                      </Typography>
                    </Grid>
                  </Grid>
                </Stack>
              </Box>

              {/* Submarines */}
              <Box
                component="section"
                aria-labelledby="submarines-heading"
                sx={{
                  borderRadius: 3,
                  p: 2,
                  background:
                    'linear-gradient(145deg, rgba(15,23,42,0.97), rgba(15,23,42,0.85))',
                }}
              >
                <Stack spacing={2}>
                  <Typography id="submarines-heading" variant="h6">
                    Submarines
                  </Typography>
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
                              <Tooltip title="Mit Pfeiltasten steuern">
                                <IconButton
                                  size="small"
                                  color={
                                    selectedSubId === s.id
                                      ? 'secondary'
                                      : 'default'
                                  }
                                  onClick={() => setSelectedSubId(s.id)}
                                  aria-label="Submarine mit Pfeiltasten steuern"
                                >
                                  <ArrowUpward fontSize="small" />
                                </IconButton>
                              </Tooltip>
                              <Tooltip title="Geradeaus">
                                <IconButton
                                  size="small"
                                  onClick={() => pilotSubmarine(s.id, 'C')}
                                  aria-label="Submarine geradeaus"
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
                                  aria-label="Submarine aufsteigen"
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
                                  aria-label="Submarine abtauchen"
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
                                  aria-label="Submarine Foto aufnehmen"
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
                                  aria-label="Submarine lokalisieren"
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
                                  aria-label="Submarine nach links drehen"
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
                                  aria-label="Submarine nach rechts drehen"
                                >
                                  <RotateRight fontSize="small" />
                                </IconButton>
                              </Tooltip>
                              <Tooltip title="Kill submarine">
                                <IconButton
                                  size="small"
                                  color="error"
                                  onClick={() => killSubmarine(s.id)}
                                  aria-label="Submarine stoppen"
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
              </Box>

              {/* Submarine Live-View */}
              <Box
                component="section"
                aria-labelledby="liveview-heading"
                sx={{
                  borderRadius: 3,
                  p: 2,
                  background:
                    'linear-gradient(145deg, rgba(15,23,42,0.97), rgba(15,23,42,0.85))',
                }}
              >
                <Stack spacing={2}>
                  <Stack
                    direction="row"
                    alignItems="center"
                    justifyContent="space-between"
                    flexWrap="wrap"
                    gap={1}
                  >
                    <Stack direction="row" alignItems="center" spacing={1}>
                      <Visibility sx={{ color: '#38bdf8' }} />
                      <Typography id="liveview-heading" variant="h6">
                        Submarine Live-View
                      </Typography>
                    </Stack>
                    <Stack direction="row" spacing={1} alignItems="center">
                      <Tooltip title="Bild jetzt laden">
                        <span>
                          <IconButton
                            color="primary"
                            onClick={() => fetchLiveView(selectedSubId)}
                            disabled={liveViewData.loading || activeSubs.length === 0}
                            size="small"
                          >
                            <Refresh />
                          </IconButton>
                        </span>
                      </Tooltip>
                      <Tooltip title="Foto aufnehmen und laden">
                        <span>
                          <IconButton
                            color="secondary"
                            onClick={async () => {
                              if (selectedSubId) {
                                await pilotSubmarine(selectedSubId, 'None', 'take_photo')
                                // Kurz warten, bis das Bild verarbeitet ist
                                setTimeout(() => fetchLiveView(selectedSubId), 1500)
                              }
                            }}
                            disabled={!selectedSubId || activeSubs.length === 0}
                            size="small"
                          >
                            <PhotoCamera />
                          </IconButton>
                        </span>
                      </Tooltip>
                      <Chip
                        label={autoRefreshLiveView ? 'Auto-Refresh AN' : 'Auto-Refresh AUS'}
                        size="small"
                        color={autoRefreshLiveView ? 'secondary' : 'default'}
                        onClick={() => setAutoRefreshLiveView(!autoRefreshLiveView)}
                        sx={{ cursor: 'pointer' }}
                      />
                    </Stack>
                  </Stack>
                  <Typography variant="body2" color="text.secondary">
                    Zeigt das letzte Kamerabild des ausgewählten Submarines.
                    {selectedSubId && ` Aktiv: ${selectedSubId}`}
                  </Typography>
                  <Box
                    sx={{
                      display: 'flex',
                      justifyContent: 'center',
                      alignItems: 'center',
                      minHeight: 300,
                      maxHeight: 500,
                      borderRadius: 2,
                      overflow: 'hidden',
                      background: 'rgba(0,0,0,0.4)',
                      border: '1px solid rgba(56,189,248,0.3)',
                    }}
                  >
                    {liveViewData.loading ? (
                      <Typography color="text.secondary">Lade Bild...</Typography>
                    ) : liveViewData.picture ? (
                      <img
                        src={liveViewData.picture}
                        alt={`Live-View von ${liveViewData.id}`}
                        style={{
                          maxWidth: '100%',
                          maxHeight: '500px',
                          objectFit: 'contain',
                        }}
                      />
                    ) : activeSubs.length === 0 ? (
                      <Typography color="text.secondary">
                        Kein Submarine aktiv. Starte ein Submarine und mache ein Foto.
                      </Typography>
                    ) : (
                      <Typography color="text.secondary">
                        Kein Bild verfügbar. Klicke auf das Kamera-Icon, um ein Foto aufzunehmen.
                      </Typography>
                    )}
                  </Box>
                  {liveViewData.timestamp && (
                    <Typography variant="caption" color="text.secondary" align="center">
                      Aufgenommen: {new Date(liveViewData.timestamp).toLocaleString()}
                    </Typography>
                  )}
                </Stack>
              </Box>

              {/* Log */}
              <Box
                component="section"
                aria-labelledby="log-heading"
                sx={{
                  borderRadius: 3,
                  p: 2,
                  flex: 1,
                  minHeight: 180,
                  maxHeight: 260,
                  display: 'flex',
                  flexDirection: 'column',
                  background:
                    'linear-gradient(145deg, rgba(15,23,42,0.98), rgba(15,23,42,0.9))',
                }}
              >
                <Stack
                  direction="row"
                  alignItems="center"
                  justifyContent="space-between"
                >
                  <Typography id="log-heading" variant="h6">
                    Log
                  </Typography>
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
                  aria-live="polite"
                >
                  {logs.map((l, i) => (
                    <Typography key={i} variant="body2">
                      {l}
                    </Typography>
                  ))}
                </Box>
              </Box>
            </Stack>
          </Paper>
        </Box>
      </Box>
    </ThemeProvider>
  )
}

export default App
