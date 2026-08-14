String formatRemaining(int remainingMs) {
  final int totalSeconds = (remainingMs / 1000).ceil().clamp(0, 1 << 31);
  final int minutes = totalSeconds ~/ 60;
  final int seconds = totalSeconds % 60;
  if (minutes == 0) return '${seconds}s';
  if (seconds == 0) return '${minutes}m';
  return '${minutes}m ${seconds}s';
}

String formatMinutesSeconds(int totalSeconds) {
  final int m = totalSeconds ~/ 60;
  final int s = totalSeconds % 60;
  return '${m.toString().padLeft(2, '0')}:${s.toString().padLeft(2, '0')}';
}

String surfaceLabel(String surfaceName) {
  switch (surfaceName) {
    case 'youtubeShorts':
      return 'Shorts';
    case 'instagramReels':
      return 'Reels';
    case 'wholeApp':
      return 'Whole app';
    default:
      return surfaceName;
  }
}
