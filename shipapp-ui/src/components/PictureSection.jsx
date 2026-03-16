import { Box, IconButton, Stack, Tooltip, Typography } from '@mui/material'
import { PhotoCamera } from '@mui/icons-material'

function PictureSection({
  lastPicture,
  activeSubs,
  selectedSubId,
  setSelectedSubId,
  pilotSubmarine,
}) {
  const hasActiveSub = activeSubs.length > 0

  const handleTakePhoto = () => {
    if (!hasActiveSub) return
    const targetId =
      selectedSubId && activeSubs.some((s) => s.id === selectedSubId)
        ? selectedSubId
        : activeSubs[0].id
    setSelectedSubId(targetId)
    pilotSubmarine(targetId, 'None', 'take_photo')
  }

  return (
    <Box
      component="section"
      aria-labelledby="picture-heading"
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
            <PhotoCamera sx={{ color: '#38bdf8' }} />
            <Typography id="picture-heading" variant="h6">
              Kamerabild
            </Typography>
          </Stack>
          <Tooltip title="Foto aufnehmen und anzeigen">
            <span>
              <IconButton
                color="secondary"
                onClick={handleTakePhoto}
                disabled={!hasActiveSub}
                size="small"
              >
                <PhotoCamera />
              </IconButton>
            </span>
          </Tooltip>
        </Stack>
        <Typography variant="body2" color="text.secondary">
          Wird angezeigt, sobald ein Foto aufgenommen wurde.
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
          {lastPicture.loading ? (
            <Typography color="text.secondary">Lade Bild...</Typography>
          ) : lastPicture.picture || lastPicture.pictureUrl ? (
            <img
              src={lastPicture.picture || lastPicture.pictureUrl}
              alt={
                lastPicture.id
                  ? `Foto von ${lastPicture.id}`
                  : 'Kamerabild'
              }
              style={{
                maxWidth: '100%',
                maxHeight: '500px',
                objectFit: 'contain',
              }}
            />
          ) : (
            <Typography color="text.secondary">
              Noch kein Foto. Submarine wählen und Kamera-Icon klicken (oder bei
              einer Sub in der Liste).
            </Typography>
          )}
        </Box>
        {lastPicture.timestamp && (
          <Typography
            variant="caption"
            color="text.secondary"
            align="center"
          >
            Aufgenommen: {new Date(lastPicture.timestamp).toLocaleString()}
          </Typography>
        )}
      </Stack>
    </Box>
  )
}

export default PictureSection

