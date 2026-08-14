import 'dart:math' show Random;
import 'package:flutter/material.dart' hide Card;
import '../../domain/blackjack.dart';
import '../app/app_state.dart';
import '../shared/panel.dart';
import '../shared/tokens.dart';

/// Verbal copy avoids gambling/wager vocabulary. The UI says "hand", "round",
/// "earn", and "grant" - never bet, wager, pot, or chips.
///
/// This screen wraps the canonical [BlackjackEngine] from `domain/blackjack.dart`.
/// Engine types used:
///   Card / Rank / Suit / BlackjackOutcome / BlackjackHand / BlackjackEngine / PlayerAction
class BlackjackScreen extends StatefulWidget {
  final String packageName;
  final AppState appState;
  final Random? rngOverride;
  final void Function(bool won) onFinished;

  const BlackjackScreen({
    super.key,
    required this.packageName,
    required this.appState,
    required this.onFinished,
    this.rngOverride,
  });

  @override
  State<BlackjackScreen> createState() => _BlackjackScreenState();
}

class _BlackjackScreenState extends State<BlackjackScreen> {
  late BlackjackEngine _engine;
  late BlackjackHand _hand;
  bool _settled = false;

  @override
  void initState() {
    super.initState();
    _engine = BlackjackEngine(random: widget.rngOverride);
    _deal();
  }

  void _deal() {
    _hand = _engine.dealHand();
    _settled = _hand.isComplete;
  }

  void _onHit() {
    if (_settled) return;
    setState(() {
      _hand = _engine.play(_hand, PlayerAction.hit);
      if (_hand.isComplete) _settled = true;
    });
  }

  void _onStand() {
    if (_settled) return;
    setState(() {
      _hand = _engine.play(_hand, PlayerAction.stand);
      _settled = _hand.isComplete;
    });
  }

  bool get _playerWon {
    final o = _hand.outcome;
    if (o == null) return false;
    return o == BlackjackOutcome.playerWin ||
        o == BlackjackOutcome.playerBlackjack;
  }

  bool get _isPush => _hand.outcome == BlackjackOutcome.push;

  String _cardLabel(Card c) {
    final r = switch (c.rank) {
      Rank.ace => 'A',
      Rank.two => '2',
      Rank.three => '3',
      Rank.four => '4',
      Rank.five => '5',
      Rank.six => '6',
      Rank.seven => '7',
      Rank.eight => '8',
      Rank.nine => '9',
      Rank.ten => '10',
      Rank.jack => 'J',
      Rank.queen => 'Q',
      Rank.king => 'K',
    };
    return r;
  }

  String _handStatusLabel(List<Card> cards, bool isPlayer) {
    if (isBust(cards)) return 'BUST';
    if (isBlackjack(cards)) return 'BLACKJACK';
    if (!isPlayer) return '';
    return '';
  }

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    final bool reduceMotion = MediaQuery.of(context).disableAnimations;

    final bool dealerRevealed = _hand.isComplete;
    final String dealerTotalLabel = dealerRevealed
        ? '${_hand.dealerValue}'
        : '?';
    final String playerTotalLabel = '${_hand.playerValue}';

    return Scaffold(
      appBar: AppBar(
        title: Text('BLACKJACK', style: SonderTextStyles.displayMedium),
      ),
      body: ListView(
        padding: const EdgeInsets.all(SonderSpacing.lg),
        children: [
          Semantics(
            label:
                'Rule: one hand. Win earns 5 minutes. Loss locks 10 minutes. Dealer stands on all 17.',
            child: Container(
              padding: const EdgeInsets.all(SonderSpacing.sm),
              decoration: BoxDecoration(
                color: theme.colorScheme.primary.withValues(alpha: 0.08),
                border: Border.all(
                  color: theme.colorScheme.primary.withValues(alpha: 0.2),
                  width: 1.25,
                ),
                borderRadius: SonderRadii.panel,
              ),
              child: Text(
                'RULE  ·  One hand. Win earns 5 min. Push replays. Dealer stands on all 17.',
                style: SonderTextStyles.caption.copyWith(
                  color: theme.colorScheme.primary,
                ),
                textAlign: TextAlign.center,
              ),
            ),
          ),
          const SizedBox(height: SonderSpacing.lg),
          _HandPanel(
            title: 'DEALER',
            totalLabel: dealerTotalLabel,
            cards: _hand.dealerCards,
            revealed: dealerRevealed,
            reduceMotion: reduceMotion,
            statusLabel: _settled
                ? _handStatusLabel(_hand.dealerCards, false)
                : null,
            cardLabel: _cardLabel,
          ),
          const SizedBox(height: SonderSpacing.md),
          _HandPanel(
            title: 'YOU',
            totalLabel: playerTotalLabel,
            cards: _hand.playerCards,
            revealed: true,
            reduceMotion: reduceMotion,
            statusLabel: _settled
                ? (_hand.playerBust
                      ? 'BUST'
                      : isBlackjack(_hand.playerCards)
                      ? 'BLACKJACK'
                      : _hand.isComplete
                      ? 'STOOD'
                      : null)
                : null,
            highlight: true,
            cardLabel: _cardLabel,
          ),
          const SizedBox(height: SonderSpacing.lg),
          if (!_settled) ...[
            Row(
              children: [
                Expanded(
                  child: Semantics(
                    button: true,
                    label: 'Hit, draw one card',
                    child: SizedBox(
                      height: 48,
                      child: OutlinedButton(
                        onPressed: _onHit,
                        style: OutlinedButton.styleFrom(
                          side: BorderSide(
                            color: theme.colorScheme.outline,
                            width: 2,
                          ),
                          shape: const RoundedRectangleBorder(
                            borderRadius: SonderRadii.button,
                          ),
                          textStyle: SonderTextStyles.displaySmall,
                        ),
                        child: const Text('HIT'),
                      ),
                    ),
                  ),
                ),
                const SizedBox(width: SonderSpacing.md),
                Expanded(
                  child: Semantics(
                    button: true,
                    label: 'Stand, end your turn',
                    child: SizedBox(
                      height: 48,
                      child: ElevatedButton(
                        onPressed: _onStand,
                        style: ElevatedButton.styleFrom(
                          backgroundColor: theme.colorScheme.primary,
                          foregroundColor: theme.colorScheme.onPrimary,
                          shape: RoundedRectangleBorder(
                            borderRadius: SonderRadii.button,
                            side: BorderSide(
                              color: theme.colorScheme.outline,
                              width: 2,
                            ),
                          ),
                          textStyle: SonderTextStyles.displaySmall,
                        ),
                        child: const Text('STAND'),
                      ),
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: SonderSpacing.sm),
            Text(
              'No wagers. Just one hand.',
              style: SonderTextStyles.caption.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
              textAlign: TextAlign.center,
            ),
          ] else ...[
            _ResultPanel(
              outcome: _hand.outcome!,
              playerWon: _playerWon,
              isPush: _isPush,
              onContinue: () {
                if (_isPush) {
                  setState(_deal);
                  return;
                }
                widget.onFinished(_playerWon);
              },
            ),
          ],
        ],
      ),
    );
  }
}

class _HandPanel extends StatelessWidget {
  final String title;
  final String totalLabel;
  final List<Card> cards;
  final bool revealed;
  final bool reduceMotion;
  final String? statusLabel;
  final bool highlight;
  final String Function(Card) cardLabel;

  const _HandPanel({
    required this.title,
    required this.totalLabel,
    required this.cards,
    required this.revealed,
    required this.reduceMotion,
    required this.cardLabel,
    this.statusLabel,
    this.highlight = false,
  });

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    return SonderPanel(
      color: highlight ? theme.colorScheme.surface : null,
      borderWidth: highlight ? 2.25 : 2,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Text(
                title,
                style: SonderTextStyles.labelMono.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                ),
              ),
              const Spacer(),
              Semantics(
                label: '$title total $totalLabel',
                child: Container(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 8,
                    vertical: 4,
                  ),
                  decoration: BoxDecoration(
                    color: theme.colorScheme.outline.withValues(alpha: 0.12),
                    border: Border.all(
                      color: theme.colorScheme.outline,
                      width: 1.25,
                    ),
                    borderRadius: SonderRadii.chip,
                  ),
                  child: Text(
                    totalLabel,
                    style: SonderTextStyles.displaySmall.copyWith(
                      fontSize: 14,
                      color: theme.colorScheme.onSurface,
                    ),
                  ),
                ),
              ),
              if (statusLabel != null && statusLabel!.isNotEmpty) ...[
                const SizedBox(width: SonderSpacing.sm),
                SonderStatusChip(
                  label: statusLabel!,
                  background: statusLabel == 'BUST'
                      ? theme.colorScheme.error
                      : theme.colorScheme.primary,
                  foreground: Colors.white,
                ),
              ],
            ],
          ),
          const SizedBox(height: SonderSpacing.md),
          Semantics(
            label: '$title cards: ${cards.map(cardLabel).join(', ')}',
            child: Wrap(
              spacing: SonderSpacing.sm,
              runSpacing: SonderSpacing.sm,
              children: [
                for (int i = 0; i < cards.length; i++)
                  _CardTile(
                    card: cards[i],
                    label: cardLabel(cards[i]),
                    faceDown:
                        !revealed &&
                        i == 1 &&
                        title == 'DEALER' &&
                        cards.length == 2,
                    reduceMotion: reduceMotion,
                  ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _CardTile extends StatelessWidget {
  final Card card;
  final String label;
  final bool faceDown;
  final bool reduceMotion;

  const _CardTile({
    required this.card,
    required this.label,
    required this.faceDown,
    required this.reduceMotion,
  });

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    final bool isRed = card.suit == Suit.hearts || card.suit == Suit.diamonds;
    final String suitGlyph = switch (card.suit) {
      Suit.hearts => '♥',
      Suit.diamonds => '♦',
      Suit.clubs => '♣',
      Suit.spades => '♠',
    };

    if (faceDown) {
      return Container(
        width: 56,
        height: 78,
        decoration: BoxDecoration(
          color: theme.colorScheme.primary.withValues(alpha: 0.18),
          border: Border.all(color: theme.colorScheme.outline, width: 1.75),
          borderRadius: BorderRadius.circular(6),
        ),
        alignment: Alignment.center,
        child: Text(
          '◆',
          style: TextStyle(fontSize: 20, color: theme.colorScheme.primary),
        ),
      );
    }

    final Widget content = Container(
      width: 56,
      height: 78,
      decoration: BoxDecoration(
        color: theme.colorScheme.surface,
        border: Border.all(color: theme.colorScheme.outline, width: 1.75),
        borderRadius: BorderRadius.circular(6),
      ),
      padding: const EdgeInsets.all(6),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            label,
            style: SonderTextStyles.displaySmall.copyWith(
              fontSize: 14,
              color: isRed
                  ? theme.colorScheme.error
                  : theme.colorScheme.onSurface,
            ),
          ),
          Text(
            suitGlyph,
            style: TextStyle(
              fontSize: 16,
              color: isRed
                  ? theme.colorScheme.error
                  : theme.colorScheme.onSurface,
            ),
          ),
          const Spacer(),
          Align(
            alignment: Alignment.bottomRight,
            child: Text(
              suitGlyph,
              style: TextStyle(
                fontSize: 12,
                color: isRed
                    ? theme.colorScheme.error
                    : theme.colorScheme.onSurfaceVariant,
              ),
            ),
          ),
        ],
      ),
    );

    if (reduceMotion) return content;
    return AnimatedSwitcher(
      duration: const Duration(milliseconds: 140),
      child: KeyedSubtree(
        key: ValueKey<String>('${card.suit.name}-${card.rank.name}'),
        child: content,
      ),
    );
  }
}

class _ResultPanel extends StatelessWidget {
  final BlackjackOutcome outcome;
  final bool playerWon;
  final bool isPush;
  final VoidCallback onContinue;

  const _ResultPanel({
    required this.outcome,
    required this.playerWon,
    required this.isPush,
    required this.onContinue,
  });

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    final String headline;
    final String sub;
    final Color accent;
    final IconData icon;
    final String buttonLabel;
    final String semanticLabel;

    if (isPush) {
      headline = 'PUSH';
      sub = 'No grant, no lock. Play another hand.';
      accent = theme.colorScheme.onSurfaceVariant;
      icon = Icons.sync;
      buttonLabel = 'PLAY AGAIN';
      semanticLabel = 'Push, no grant, no lock. Play again.';
    } else if (playerWon) {
      headline = 'YOU EARNED IT';
      sub = '5 minutes granted. Returning to your app.';
      accent = theme.colorScheme.tertiary;
      icon = Icons.check_circle_outline;
      buttonLabel = 'CONTINUE  ·  5 MIN';
      semanticLabel = 'Win, 5 minutes earned';
    } else {
      headline = 'LOCKED';
      sub = 'Locked for 10 minutes. Take a breather.';
      accent = theme.colorScheme.error;
      icon = Icons.lock_outline;
      buttonLabel = 'DONE  ·  LOCKED 10 MIN';
      semanticLabel = 'Loss, locked 10 minutes';
    }

    return Semantics(
      label: semanticLabel,
      liveRegion: true,
      child: SonderPanel(
        borderWidth: 2.25,
        child: Column(
          children: [
            Icon(icon, size: 32, color: accent),
            const SizedBox(height: SonderSpacing.sm),
            Text(
              headline,
              style: SonderTextStyles.displayMedium.copyWith(color: accent),
            ),
            const SizedBox(height: SonderSpacing.xs),
            Text(
              sub,
              style: SonderTextStyles.body.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: SonderSpacing.md),
            SonderPrimaryButton(
              label: buttonLabel,
              onPressed: onContinue,
              semanticLabel: buttonLabel,
            ),
            if (isPush) ...[
              const SizedBox(height: SonderSpacing.xs),
              Text(
                'Push does not grant or lock time.',
                style: SonderTextStyles.caption.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }
}
