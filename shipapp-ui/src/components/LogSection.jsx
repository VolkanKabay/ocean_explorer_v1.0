import { Box, Button, Stack, Typography } from '@mui/material'
import { ClearAll } from '@mui/icons-material'

function LogSection({ logs, clearAll }) {
  return (
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
  )
}

export default LogSection

