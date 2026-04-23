import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:network_info_plus/network_info_plus.dart';

class AudioStreamPage extends StatefulWidget {
  const AudioStreamPage({super.key});

  @override
  State<AudioStreamPage> createState() => _AudioStreamPageState();
}

class _AudioStreamPageState extends State<AudioStreamPage>
    with SingleTickerProviderStateMixin {
  static const _channel =
      MethodChannel('com.iyox.wormhole/audio_stream');

  late final TabController _tabController;
  final _ipController = TextEditingController();

  bool _isSenderStreaming = false;
  bool _isReceiverConnected = false;
  String _localIp = '...';

  static const _port = 55124;

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 2, vsync: this);
    _loadLocalIp();
  }

  Future<void> _loadLocalIp() async {
    try {
      final ip = await NetworkInfo().getWifiIP();
      if (mounted) setState(() => _localIp = ip ?? 'WiFi এ নেই');
    } catch (_) {
      if (mounted) setState(() => _localIp = 'IP পাওয়া যায়নি');
    }
  }

  Future<void> _startStreaming() async {
    try {
      final ok = await _channel.invokeMethod<bool>('startStream');
      if (ok == true && mounted) {
        setState(() => _isSenderStreaming = true);
      } else if (mounted) {
        _showError('Permission দেওয়া হয়নি।');
      }
    } on PlatformException catch (e) {
      if (mounted) _showError(e.message ?? 'Error');
    }
  }

  Future<void> _stopStreaming() async {
    await _channel.invokeMethod('stopStream');
    if (mounted) setState(() => _isSenderStreaming = false);
  }

  Future<void> _connectToStream() async {
    final ip = _ipController.text.trim();
    if (ip.isEmpty) {
      _showError('Sender এর IP দিন।');
      return;
    }
    try {
      await _channel.invokeMethod<bool>(
          'startReceive', {'ip': ip, 'port': _port});
      if (mounted) setState(() => _isReceiverConnected = true);
    } on PlatformException catch (e) {
      if (mounted) _showError(e.message ?? 'Connection error');
    }
  }

  Future<void> _disconnect() async {
    await _channel.invokeMethod('stopReceive');
    if (mounted) setState(() => _isReceiverConnected = false);
  }

  void _showError(String msg) {
    ScaffoldMessenger.of(context)
        .showSnackBar(SnackBar(content: Text(msg)));
  }

  @override
  void dispose() {
    _tabController.dispose();
    _ipController.dispose();
    super.dispose();
  }

  // ─── BUILD ───────────────────────────────────────────────────────────────

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Live Audio'),
        bottom: TabBar(
          controller: _tabController,
          tabs: const [
            Tab(icon: Icon(Icons.broadcast_on_personal), text: 'Sender'),
            Tab(icon: Icon(Icons.headphones), text: 'Receiver'),
          ],
        ),
      ),
      body: TabBarView(
        controller: _tabController,
        children: [
          _buildSenderTab(scheme),
          _buildReceiverTab(scheme),
        ],
      ),
    );
  }

  // ─── SENDER TAB ──────────────────────────────────────────────────────────

  Widget _buildSenderTab(ColorScheme scheme) {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          const SizedBox(height: 24),

          // IP Card
          Container(
            padding: const EdgeInsets.symmetric(vertical: 28, horizontal: 24),
            decoration: BoxDecoration(
              color: scheme.surfaceContainerHighest,
              borderRadius: BorderRadius.circular(20),
            ),
            child: Column(
              children: [
                Icon(Icons.wifi, size: 48, color: scheme.primary),
                const SizedBox(height: 12),
                Text(
                  'আপনার IP Address',
                  style: TextStyle(
                      fontSize: 13, color: scheme.onSurfaceVariant),
                ),
                const SizedBox(height: 8),
                SelectableText(
                  _localIp,
                  style: TextStyle(
                    fontSize: 30,
                    fontWeight: FontWeight.bold,
                    color: scheme.primary,
                    letterSpacing: 1.5,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  'Port: $_port',
                  style: TextStyle(
                      fontSize: 13, color: scheme.outline),
                ),
              ],
            ),
          ),

          const SizedBox(height: 32),

          // Action button
          if (!_isSenderStreaming) ...[
            FilledButton.icon(
              onPressed: _startStreaming,
              icon: const Icon(Icons.stream),
              label: const Text('Stream শুরু করুন'),
              style: FilledButton.styleFrom(
                  minimumSize: const Size.fromHeight(56)),
            ),
          ] else ...[
            _StatusChip(label: 'Streaming চলছে...', active: true),
            const SizedBox(height: 20),
            OutlinedButton.icon(
              onPressed: _stopStreaming,
              icon: const Icon(Icons.stop_circle_outlined),
              label: const Text('বন্ধ করুন'),
              style: OutlinedButton.styleFrom(
                  minimumSize: const Size.fromHeight(56),
                  foregroundColor: scheme.error),
            ),
          ],

          const SizedBox(height: 20),
          Text(
            'Receiver ফোনে উপরের IP address টা লিখুন।\nদুটো ফোনকে একই WiFi-তে থাকতে হবে।',
            textAlign: TextAlign.center,
            style:
                TextStyle(fontSize: 13, color: scheme.onSurfaceVariant),
          ),

          // Android version warning
          if (!Platform.isAndroid ||
              int.tryParse(
                          RegExp(r'\d+')
                                  .firstMatch(
                                      Platform.operatingSystemVersion)
                                  ?.group(0) ??
                              '0') !=
                      null &&
                  (int.tryParse(RegExp(r'\d+')
                              .firstMatch(
                                  Platform.operatingSystemVersion)
                              ?.group(0) ??
                          '0') ??
                      0) <
                      10) ...[
            const SizedBox(height: 16),
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: scheme.errorContainer,
                borderRadius: BorderRadius.circular(12),
              ),
              child: Text(
                '⚠ System audio capture এর জন্য Android 10+ দরকার।',
                style: TextStyle(color: scheme.onErrorContainer),
                textAlign: TextAlign.center,
              ),
            ),
          ],
        ],
      ),
    );
  }

  // ─── RECEIVER TAB ────────────────────────────────────────────────────────

  Widget _buildReceiverTab(ColorScheme scheme) {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(24),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          const SizedBox(height: 32),
          Icon(Icons.headphones, size: 80, color: scheme.primary),
          const SizedBox(height: 8),
          Text(
            'Sender এর IP দিয়ে connect করুন',
            textAlign: TextAlign.center,
            style: TextStyle(
                fontSize: 14, color: scheme.onSurfaceVariant),
          ),
          const SizedBox(height: 32),

          // IP input
          TextField(
            controller: _ipController,
            enabled: !_isReceiverConnected,
            keyboardType:
                const TextInputType.numberWithOptions(decimal: true),
            decoration: InputDecoration(
              labelText: 'Sender এর IP Address',
              hintText: '192.168.x.x',
              border: const OutlineInputBorder(),
              prefixIcon: const Icon(Icons.wifi_find),
            ),
          ),

          const SizedBox(height: 24),

          if (!_isReceiverConnected) ...[
            FilledButton.icon(
              onPressed: _connectToStream,
              icon: const Icon(Icons.play_arrow),
              label: const Text('Connect করুন'),
              style: FilledButton.styleFrom(
                  minimumSize: const Size.fromHeight(56)),
            ),
          ] else ...[
            _StatusChip(label: 'Audio পাচ্ছেন...', active: true),
            const SizedBox(height: 20),
            OutlinedButton.icon(
              onPressed: _disconnect,
              icon: const Icon(Icons.link_off),
              label: const Text('Disconnect করুন'),
              style: OutlinedButton.styleFrom(
                  minimumSize: const Size.fromHeight(56),
                  foregroundColor: scheme.error),
            ),
          ],

          const SizedBox(height: 20),
          Text(
            'Sender এ আগে "Stream শুরু করুন" চাপুন,\nতারপর এখানে Connect করুন।',
            textAlign: TextAlign.center,
            style:
                TextStyle(fontSize: 13, color: scheme.onSurfaceVariant),
          ),
        ],
      ),
    );
  }
}

// ─── Helper widget ────────────────────────────────────────────────────────────

class _StatusChip extends StatelessWidget {
  final String label;
  final bool active;

  const _StatusChip({required this.label, required this.active});

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        Icon(Icons.circle,
            size: 10, color: active ? Colors.green : Colors.grey),
        const SizedBox(width: 8),
        Text(label,
            style: const TextStyle(fontWeight: FontWeight.w500)),
      ],
    );
  }
}
