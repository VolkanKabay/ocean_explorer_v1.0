import { useEffect, useState, useCallback } from 'react'
import { AppBar, Box, CssBaseline, Paper, Stack, Toolbar, Typography, createTheme, ThemeProvider, Chip } from '@mui/material'
import { DirectionsBoat, Subtitles } from '@mui/icons-material'
import './App.css'
import ShipLaunchSection from './components/ShipLaunchSection.jsx'
import NavigationSection from './components/NavigationSection.jsx'
import SubmarinesSection from './components/SubmarinesSection.jsx'
import PictureSection from './components/PictureSection.jsx'
import SubmarineHistorySection from './components/SubmarineHistorySection.jsx'
import SubmarineTrackMapSection from './components/SubmarineTrackMapSection.jsx'
import LogSection from './components/LogSection.jsx'

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
  const [subHistory, setSubHistory] = useState([])
  const [isLaunching, setIsLaunching] = useState(false)
  const [isPatrolling, setIsPatrolling] = useState(false)
  const [patrolTimerId, setPatrolTimerId] = useState(null)
  const [lastScan, setLastScan] = useState(null)
  const [lastRadar, setLastRadar] = useState(null)
  const [launchParams, setLaunchParams] = useState({
    name: 'Explorer1',
    x: 1,
    y: 1,
    dx: 1,
    dy: 1,
  })

  const [lastPicture, setLastPicture] = useState({
    picture: null,
    pictureUrl: null,
    id: null,
    timestamp: null,
    loading: false,
  })
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

  const refreshSubHistory = useCallback(async () => {
    try {
      const res = await apiGet('/submarine/history')
      setSubHistory(Array.isArray(res.submarines) ? res.submarines : [])
    } catch (e) {
      appendLog(`Fehler beim Laden der Submarine-Historie: ${e.message}`)
    }
  }, [appendLog])

  useEffect(() => {
    refreshState()
    refreshSubHistory()
    const id = setInterval(() => {
      refreshState()
      refreshSubHistory()
    }, 2000)
    return () => clearInterval(id)
  }, [refreshState, refreshSubHistory])

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
      setLastScan(res)
      await refreshState()
    } catch (e) {
      appendLog(`Scan fehlgeschlagen: ${e.message}`)
    }
  }

  const patrolStep = useCallback(async () => {
    try {
      const res = await apiPost('/radar')
      const echos = Array.isArray(res.echos) ? res.echos : []
      setLastRadar(res)

      if (echos.length === 0) {
        await sendNavigate('Center', 'Forward')
      } else {
        setLogs((prev) => [
          ...prev.slice(-199),
          `[${new Date().toLocaleTimeString()}] Patrol: Radar-Echos=${echos.length}, Hindernis erkannt – weiche aus`,
        ])
        await sendNavigate(Math.random() < 0.5 ? 'Left' : 'Right', 'Forward')
      }
    } catch (e) {
      appendLog(`Patrouille/Radar fehlgeschlagen: ${e.message}`)
    }
  }, [appendLog, sendNavigate])

  const startPatrol = useCallback(() => {
    if (isPatrolling || patrolTimerId) return
    const id = setInterval(() => {
      patrolStep()
    }, 1200)
    setPatrolTimerId(id)
    setIsPatrolling(true)
    appendLog('Patrouillenmodus gestartet')
  }, [appendLog, isPatrolling, patrolStep, patrolTimerId])

  const stopPatrol = useCallback(() => {
    if (patrolTimerId) {
      clearInterval(patrolTimerId)
    }
    setPatrolTimerId(null)
    if (isPatrolling) {
      appendLog('Patrouillenmodus gestoppt')
    }
    setIsPatrolling(false)
  }, [appendLog, isPatrolling, patrolTimerId])

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

  const fetchPictureAfterTakePhoto = useCallback(
    async (subId, maxAttempts = 8, intervalMs = 800) => {
      setLastPicture((prev) => ({ ...prev, loading: true, id: subId }))
      const url = subId ? `/submarine/picture?id=${encodeURIComponent(subId)}` : '/submarine/picture'
      for (let attempt = 0; attempt < maxAttempts; attempt++) {
        await new Promise((r) => setTimeout(r, attempt === 0 ? 600 : intervalMs))
        try {
          const res = await apiGet(url)
          if (res.picture && res.hasPicture) {
            setLastPicture({
              picture: `data:image/png;base64,${res.picture}`,
              pictureUrl: null,
              id: res.id ?? subId,
              timestamp: res.timestamp ?? null,
              loading: false,
            })
            return
          }
        } catch (e) {
          appendLog(`Bild Versuch ${attempt + 1}: ${e.message}`)
        }
      }
      setLastPicture({
        picture: null,
        pictureUrl: `${API_BASE}/submarine/picture/latest?id=${encodeURIComponent(subId)}&t=${Date.now()}`,
        id: subId,
        timestamp: null,
        loading: false,
      })
    },
    [appendLog]
  )

  const pilotSubmarine = useCallback(async (subId, route, action = '') => {
    try {
      await apiPost('/submarine/pilot', { id: subId, route, action })
      appendLog(`Pilot: id=${subId}, route=${route}, action=${action}`)
      if (action === 'take_photo') {
        fetchPictureAfterTakePhoto(subId)
      }
    } catch (e) {
      appendLog(`Pilot fehlgeschlagen: ${e.message}`)
    }
  }, [appendLog, fetchPictureAfterTakePhoto])

  const activeSubs = state?.submarines ?? []

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

  useEffect(() => {
    const handler = (e) => {
      const t = e.target
      const tag = t?.tagName
      const isEditable =
        tag === 'INPUT' ||
        tag === 'TEXTAREA' ||
        tag === 'SELECT' ||
        t?.isContentEditable === true
      if (isEditable) return
      const key = e.key.toLowerCase()
      const hasSub = activeSubs.length > 0
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

      if (!hasSub) return

      const targetId =
        selectedSubId && activeSubs.some((s) => s.id === selectedSubId)
          ? selectedSubId
          : activeSubs[0].id

      if (e.key === 'ArrowUp') {
        e.preventDefault()
        pilotSubmarine(targetId, 'C')
      } else if (e.key === 'ArrowDown') {
        e.preventDefault()
        pilotSubmarine(targetId, 'DOWN')
      } else if (e.key === 'ArrowLeft') {
        e.preventDefault()
        pilotSubmarine(targetId, 'W')
      } else if (e.key === 'ArrowRight') {
        e.preventDefault()
        pilotSubmarine(targetId, 'E')
      } else if (key === 'k') {
        pilotSubmarine(targetId, 'None', 'take_photo')
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
      stopPatrol()
      await apiPost('/reset')
      appendLog('Session reset (Ship & Submarines zurückgesetzt)')
      setState(null)
      setSubHistory([])
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
              maxWidth: 1400,
              borderRadius: 4,
              p: { xs: 2, sm: 3 },
              border: '1px solid rgba(148,163,184,0.35)',
              background:
                'radial-gradient(ellipse at top left, rgba(56,189,248,0.18), transparent 55%), radial-gradient(ellipse at bottom right, rgba(34,197,94,0.15), transparent 60%), rgba(15,23,42,0.98)',
            }}
          >
            <Stack spacing={3}>
              <ShipLaunchSection
                launchParams={launchParams}
                setLaunchParams={setLaunchParams}
                state={state}
                isLaunching={isLaunching}
                handleLaunch={handleLaunch}
                resetSession={resetSession}
              />

              <NavigationSection
                sendNavigate={sendNavigate}
                sendScan={sendScan}
                sendRadar={async () => {
                  try {
                    const res = await apiPost('/radar')
                    setLastRadar(res)
                    appendLog(
                      `Radar: ${Array.isArray(res.echos) ? res.echos.length : 0} Echos`
                    )
                  } catch (e) {
                    appendLog(`Radar fehlgeschlagen: ${e.message}`)
                  }
                }}
                lastScan={lastScan}
                lastRadar={lastRadar}
                isPatrolling={isPatrolling}
                startPatrol={startPatrol}
                stopPatrol={stopPatrol}
              />

              <SubmarinesSection
                activeSubs={activeSubs}
                selectedSubId={selectedSubId}
                setSelectedSubId={setSelectedSubId}
                startSubmarine={startSubmarine}
                pilotSubmarine={pilotSubmarine}
                killSubmarine={killSubmarine}
              />

              <PictureSection
                lastPicture={lastPicture}
                activeSubs={activeSubs}
                selectedSubId={selectedSubId}
                setSelectedSubId={setSelectedSubId}
                pilotSubmarine={pilotSubmarine}
              />

              <SubmarineTrackMapSection
                apiGet={apiGet}
                activeSubs={activeSubs}
                selectedSubId={selectedSubId}
                setSelectedSubId={setSelectedSubId}
              />

              <SubmarineHistorySection history={subHistory} />

              <LogSection logs={logs} clearAll={clearAll} />
            </Stack>
          </Paper>
        </Box>
      </Box>
    </ThemeProvider>
  )
}

export default App
