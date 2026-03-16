import { Box, Button, Divider, Grid, Stack, TextField, Typography } from '@mui/material'
import { PlayArrow } from '@mui/icons-material'

function ShipLaunchSection({
  launchParams,
  setLaunchParams,
  state,
  isLaunching,
  handleLaunch,
  resetSession,
}) {
  return (
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
          Verbinde dein Forschungsschiff mit dem Ocean-Server und setze die
          Startposition.
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
          <Button variant="outlined" color="inherit" onClick={resetSession}>
            Reset Session
          </Button>
        </Stack>
        {state?.ship && (
          <>
            <Divider sx={{ my: 1.5 }} />
            <Stack spacing={0.5}>
              <Typography variant="subtitle2">Aktueller Zustand</Typography>
              <Typography variant="body2">
                Sektor: [{state.ship.sector?.x}, {state.ship.sector?.y}]
              </Typography>
              <Typography variant="body2">
                Richtung: [{state.ship.dir?.x}, {state.ship.dir?.y}]
              </Typography>
            </Stack>
          </>
        )}
      </Stack>
    </Box>
  )
}

export default ShipLaunchSection

