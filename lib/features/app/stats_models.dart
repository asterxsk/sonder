class TargetStats {
  final int opens;
  final int wins;
  final int losses;

  const TargetStats({this.opens = 0, this.wins = 0, this.losses = 0});

  TargetStats copyWith({int? opens, int? wins, int? losses}) => TargetStats(
    opens: opens ?? this.opens,
    wins: wins ?? this.wins,
    losses: losses ?? this.losses,
  );
}

class StatsSnapshot {
  final int interceptedOpens;
  final int wins;
  final int losses;
  final int timeSavedMs;
  final int lockedMs;

  const StatsSnapshot({
    this.interceptedOpens = 0,
    this.wins = 0,
    this.losses = 0,
    this.timeSavedMs = 0,
    this.lockedMs = 0,
  });

  int get handsPlayed => wins + losses;

  int get winRatePercent => handsPlayed == 0 ? 0 : (wins * 100 ~/ handsPlayed);
}
