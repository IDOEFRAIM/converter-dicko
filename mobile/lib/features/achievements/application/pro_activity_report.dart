import 'dart:typed_data';

import 'package:pdf/pdf.dart';
import 'package:pdf/widgets.dart' as pw;

import '../../../shared/models/money.dart';
import '../../../shared/utils/date_formatting.dart';
import '../../orders/models/order_models.dart';
import '../models/achievement_models.dart';

/// Rapport d'activite PDF du profil PRO (mission "differenciation
/// marketing", section "INTERFACE PRO" : "Livre des Gains -> Remplace par un
/// Rapport d'activite PDF (exportable)"). Purement une mise en forme des
/// donnees deja chargees par `MyGainsController` — jamais un nouvel appel
/// reseau, jamais un montant recalcule ou invente (memes chaines decimales
/// exactes que celles affichees a l'ecran, voir [Money]).
Future<Uint8List> buildProActivityReportPdf({
  required String fullName,
  required AchievementSummary summary,
  required List<OrderHistoryEntry> completedTransfers,
}) async {
  final doc = pw.Document();

  doc.addPage(
    pw.MultiPage(
      pageFormat: PdfPageFormat.a4,
      build: (context) => [
        pw.Text('Rapport d\'activite', style: pw.TextStyle(fontSize: 22, fontWeight: pw.FontWeight.bold)),
        pw.SizedBox(height: 4),
        pw.Text(fullName),
        pw.Text('Genere le ${DateFormatting.dayTime(DateTime.now().toUtc())}'),
        pw.SizedBox(height: 20),
        pw.Text('RESUME', style: pw.TextStyle(fontSize: 12, fontWeight: pw.FontWeight.bold)),
        pw.SizedBox(height: 8),
        _summaryRow('Volume transfere ce mois-ci',
            Money(summary.currentMonthAmountXofCompleted, AppCurrency.xof).formattedWithCurrency()),
        _summaryRow('Volume transfere au total',
            Money(summary.totalAmountXofCompleted, AppCurrency.xof).formattedWithCurrency()),
        _summaryRow('Transferts termines (total)', '${summary.completedTransferCount}'),
        pw.SizedBox(height: 24),
        pw.Text(
          'HISTORIQUE (${completedTransfers.length} dernier(s) transfert(s) termine(s))',
          style: pw.TextStyle(fontSize: 12, fontWeight: pw.FontWeight.bold),
        ),
        pw.SizedBox(height: 8),
        if (completedTransfers.isEmpty)
          pw.Text('Aucun transfert termine pour le moment.')
        else
          pw.TableHelper.fromTextArray(
            headers: const ['Reference', 'Date', 'Montant', 'Statut'],
            data: completedTransfers
                .map((entry) => [
                      entry.reference,
                      DateFormatting.dayOnly(entry.createdAt),
                      Money(entry.amountXof, AppCurrency.xof).formattedWithCurrency(),
                      entry.status.code,
                    ])
                .toList(growable: false),
          ),
      ],
    ),
  );

  return doc.save();
}

pw.Widget _summaryRow(String label, String value) {
  return pw.Padding(
    padding: const pw.EdgeInsets.symmetric(vertical: 2),
    child: pw.Row(
      mainAxisAlignment: pw.MainAxisAlignment.spaceBetween,
      children: [pw.Text(label), pw.Text(value, style: pw.TextStyle(fontWeight: pw.FontWeight.bold))],
    ),
  );
}
