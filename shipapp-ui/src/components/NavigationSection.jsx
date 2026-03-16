import { Box, Button, Grid, IconButton, Stack, Tooltip, Typography } from '@mui/material'
import { ArrowBack, ArrowDownward, ArrowForward, ArrowUpward, TravelExplore } from '@mui/icons-material'

function NavigationSection({ sendNavigate, sendScan }) {
  return (
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
          Nutze die Buttons oder die Tastatur (WASD + Q/E), um das Schiff im
          10×10 km Ozean zu bewegen.
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
                    onClick={() => sendNavigate('Left', 'Forward')}
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
                    onClick={() => sendNavigate('Center', 'Forward')}
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
                    onClick={() => sendNavigate('Right', 'Forward')}
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
                    onClick={() => sendNavigate('Left', 'Backward')}
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
                    onClick={() => sendNavigate('Center', 'Backward')}
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
                    onClick={() => sendNavigate('Right', 'Backward')}
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
        </Grid>
      </Stack>
    </Box>
  )
}

export default NavigationSection

