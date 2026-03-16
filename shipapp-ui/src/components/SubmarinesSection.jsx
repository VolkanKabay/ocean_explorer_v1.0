import {
  Box,
  Button,
  IconButton,
  List,
  ListItem,
  ListItemText,
  Stack,
  Tooltip,
  Typography,
} from '@mui/material'
import {
  ArrowDownward,
  ArrowUpward,
  DeleteForever,
  DirectionsBoat,
  PhotoCamera,
  PlayArrow,
  RotateLeft,
  RotateRight,
  TravelExplore,
} from '@mui/icons-material'

function SubmarinesSection({
  activeSubs,
  selectedSubId,
  setSelectedSubId,
  startSubmarine,
  pilotSubmarine,
  killSubmarine,
}) {
  return (
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
          erforschen. Während des Tauchgangs darf das Schiff den Sektor nicht
          wechseln.
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
                          selectedSubId === s.id ? 'secondary' : 'default'
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
                        onClick={() => pilotSubmarine(s.id, 'UP')}
                        aria-label="Submarine aufsteigen"
                      >
                        <ArrowUpward fontSize="small" />
                      </IconButton>
                    </Tooltip>
                    <Tooltip title="Abtauchen">
                      <IconButton
                        size="small"
                        onClick={() => pilotSubmarine(s.id, 'DOWN')}
                        aria-label="Submarine abtauchen"
                      >
                        <ArrowDownward fontSize="small" />
                      </IconButton>
                    </Tooltip>
                    <Tooltip title="Foto aufnehmen und in Live-View anzeigen">
                      <IconButton
                        size="small"
                        onClick={() => {
                          setSelectedSubId(s.id)
                          pilotSubmarine(s.id, 'None', 'take_photo')
                        }}
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
                        onClick={() => pilotSubmarine(s.id, 'W')}
                        aria-label="Submarine nach links drehen"
                      >
                        <RotateLeft fontSize="small" />
                      </IconButton>
                    </Tooltip>
                    <Tooltip title="Rotate right">
                      <IconButton
                        size="small"
                        onClick={() => pilotSubmarine(s.id, 'E')}
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
  )
}

export default SubmarinesSection

