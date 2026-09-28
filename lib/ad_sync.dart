/// Monotonic server-time estimate. The device's wall clock is never used.
class AdServerClock {
  final Stopwatch _watch = Stopwatch()..start();
  final List<({double time, int at, int rtt})> _samples = [];
  int get tick => _watch.elapsedMilliseconds;

  void sample(num serverMs, int start, int end) {
    if (!serverMs.isFinite || end < start || end - start > 5000) return;
    _samples.add((time: serverMs.toDouble() + (end - start) / 2, at: end, rtt: end - start));
    if (_samples.length > 5) _samples.removeAt(0);
  }

  void clear() => _samples.clear();

  double? get time {
    if (_samples.isEmpty) return null;
    final best = _samples.reduce((a, b) => a.rtt <= b.rtt ? a : b);
    return best.time + tick - best.at;
  }
}

class AdPosition {
  final int index;
  final int offsetMs;
  final int remainingMs;
  const AdPosition(this.index, this.offsetMs, this.remainingMs);
}

AdPosition? adPosition(Map<String, dynamic>? manifest, num? serverMs) {
  if (manifest == null || manifest['ready'] != true || serverMs == null || !serverMs.isFinite) return null;
  final items = manifest['items'];
  if (items is! List || items.isEmpty) return null;
  final durations = <int>[];
  for (final item in items) {
    final ms = item['duration_ms'];
    if (ms is! int || ms <= 0) return null;
    durations.add(ms);
  }
  final cycle = durations.fold<int>(0, (a, b) => a + b);
  var offset = ((serverMs - (manifest['epoch_ms'] as num)) % cycle).floor();
  for (var i = 0; i < durations.length; i++) {
    if (offset < durations[i]) return AdPosition(i, offset, durations[i] - offset);
    offset -= durations[i];
  }
  return null;
}

String adLocalId(Map ad) => ad['media_version'] == null
    ? ad['file_id'].toString()
    : '${ad['file_id']}.${ad['media_version']}.${ad['file_id'].toString().split('.').last}';
