// Same head-rendering service already used for Discord embed thumbnails - see
// ModerationSlashCommands.java's .setThumbnail("https://mc-heads.net/avatar/" + uuid).
export default function PlayerHead({
  uuid,
  size = 24,
  className = 'player-avatar',
}: {
  uuid: string
  size?: number
  className?: string
}) {
  return (
    <img
      className={className}
      src={`https://mc-heads.net/avatar/${uuid}/${size}`}
      alt=""
      width={size}
      height={size}
      loading="lazy"
    />
  )
}
