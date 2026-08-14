import 'access_status.dart';

/// Result of evaluating an enforcement snapshot at a given time.
class AccessDecision {
  final AccessStatus status;
  final int? remainingMs;

  const AccessDecision({required this.status, this.remainingMs});

  @override
  bool operator ==(Object other) =>
      other is AccessDecision &&
      other.status == status &&
      other.remainingMs == remainingMs;

  @override
  int get hashCode => Object.hash(status, remainingMs);

  @override
  String toString() => 'AccessDecision($status, remainingMs=$remainingMs)';
}
