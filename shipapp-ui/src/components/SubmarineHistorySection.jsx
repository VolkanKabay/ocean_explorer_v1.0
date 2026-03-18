import { Box, List, ListItem, ListItemText, Stack, Typography, Chip } from '@mui/material'

function formatPosition(sub) {
  const hasPos =
    sub.pos_x !== null &&
    sub.pos_x !== undefined &&
    sub.pos_y !== null &&
    sub.pos_y !== undefined &&
    sub.pos_z !== null &&
    sub.pos_z !== undefined

  if (!hasPos) return 'Letzte Position: unbekannt'

  return `Letzte Position: [${sub.pos_x.toFixed(1)}, ${sub.pos_y.toFixed(1)}, ${sub.pos_z.toFixed(1)}], Tiefe ${
    sub.depth ?? '?'
  } m`
}

function SubmarineHistorySection({ history }) {
  const submarines = history ?? []

  return (
    <Box
      component="section"
      aria-labelledby="submarine-history-heading"
      sx={{
        borderRadius: 3,
        p: 2,
        background: 'linear-gradient(145deg, rgba(15,23,42,0.97), rgba(15,23,42,0.9))',
      }}
    >
      <Stack spacing={2}>
        <Typography id="submarine-history-heading" variant="h6">
          Submarine-Historie
        </Typography>
        <Typography variant="body2" color="text.secondary">
          Übersicht aller jemals gestarteten Submarines aus der Datenbank mit Status und letzter bekannter Position.
        </Typography>

        {submarines.length === 0 ? (
          <Typography variant="body2" color="text.secondary">
            Noch keine Submarines in der Historie gespeichert.
          </Typography>
        ) : (
          <List dense sx={{ maxHeight: 260, overflowY: 'auto' }}>
            {submarines.map((sub) => (
              <ListItem key={sub.id ?? Math.random()} disableGutters>
                <ListItemText
                  primary={
                    <Stack direction="row" spacing={1} alignItems="center">
                      <Typography variant="body2" sx={{ fontWeight: 500 }}>
                        {sub.id ?? 'unbekanntes Submarine'}
                      </Typography>
                      {sub.status && (
                        <Chip
                          size="small"
                          label={sub.status}
                          color={
                            sub.status === 'active'
                              ? 'success'
                              : sub.status === 'crashed' ||
                                sub.status === 'TERMINATED' ||
                                sub.status === 'terminated'
                              ? 'error'
                              : sub.status === 'surfaced'
                              ? 'primary'
                              : 'default'
                          }
                          variant="outlined"
                        />
                      )}
                    </Stack>
                  }
                  secondary={
                    <>
                      <Typography variant="caption" display="block" color="text.secondary">
                        Erstellt: {sub.created_at ?? 'unbekannt'} / Letzt gesehen: {sub.last_seen ?? 'unbekannt'}
                      </Typography>
                      <Typography variant="caption" display="block" color="text.secondary">
                        {formatPosition(sub)}
                      </Typography>
                    </>
                  }
                />
              </ListItem>
            ))}
          </List>
        )}
      </Stack>
    </Box>
  )
}

export default SubmarineHistorySection

