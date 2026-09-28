import '../lib/ad_sync.dart';

void check(bool value, String message) {
  if (!value) throw StateError(message);
}

void main() {
  final manifest = <String, dynamic>{'ready': true, 'epoch_ms': 0,
    'items': [10000, 23000, 10000].map((ms) => {'duration_ms': ms}).toList()};
  for (final row in [
    [0, 0, 0, 10000], [9999, 0, 9999, 1], [10000, 1, 0, 23000],
    [25000, 1, 15000, 8000], [33000, 2, 0, 10000], [43000, 0, 0, 10000],
    [4300025000, 1, 15000, 8000], [-1, 2, 9999, 1],
  ]) {
    final target = adPosition(manifest, row[0])!;
    check(target.index == row[1] && target.offsetMs == row[2] && target.remainingMs == row[3], 'Boundary $row');
  }
  check(adPosition(manifest, null) == null, 'Missing clock');
  check(adPosition({...manifest, 'ready': false}, 10) == null, 'Unready manifest');
  check(adPosition({...manifest, 'items': [{'duration_ms': 0}]}, 10) == null, 'Invalid duration');
  final clock = AdServerClock();
  final end = clock.tick;
  clock.sample(25000, end - 200, end);
  check((clock.time! - 25100).abs() < 100, 'RTT midpoint');
  clock.sample(999999, end - 6000, end);
  check((clock.time! - 25100).abs() < 100, 'Reject excessive RTT');
  clock.clear();
  check(clock.time == null, 'Branch clock reset');
  print('Advertising sync: 14 checks passed');
}
