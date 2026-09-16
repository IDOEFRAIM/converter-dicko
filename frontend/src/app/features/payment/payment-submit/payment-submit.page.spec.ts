import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { PaymentSubmitPage } from './payment-submit.page';

const ORDER = {
  id: 'o1',
  reference: 'ORD-1',
  quoteId: 'q1',
  status: 'AWAITING_PAYMENT',
  amountXof: '100000.00',
  amountCny: '1176.47',
  customerRate: '85.000000',
  feeXof: '0.00',
  netAmountXof: '100000.00',
  note: null,
  cancellationReason: null,
  rejectionReason: null,
  beneficiary: { type: 'ALIPAY', fullName: 'Zhang San', identifier: 'zhang@example.com', bankName: null, bankBranch: null },
  statusHistory: [],
  createdAt: '2026-01-01T10:00:00Z',
  updatedAt: '2026-01-01T10:00:00Z',
  completedAt: null,
  cancelledAt: null,
};

const PUBLIC_SETTINGS = {
  minOrderAmountCfa: '10000',
  maxOrderAmountCfa: '2000000',
  rateLockDurationMinutes: 30,
  maxProofFileSizeBytes: 5242880,
  maxProofsPerPayment: 3,
  enabledPaymentMethods: ['MOBILE_MONEY'],
  requirePaymentProof: true,
  paymentInstructionsText: 'Envoyez le montant indique par Mobile Money, puis declarez votre paiement.',
};

describe('PaymentSubmitPage', () => {
  let fixture: ComponentFixture<PaymentSubmitPage>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PaymentSubmitPage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => 'o1' } } } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(PaymentSubmitPage);
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    httpMock.expectOne('/api/v1/orders/o1').flush({ data: ORDER, message: 'ok' });
    httpMock.expectOne('/api/settings/public').flush({ data: PUBLIC_SETTINGS, message: 'ok' });
    fixture.detectChanges();
  });

  afterEach(() => httpMock.verify());

  it('pre-fills the amount to pay from the order and offers only enabled methods', () => {
    expect(fixture.componentInstance.form.controls.receivedAmountXof.value).toBe(100000);
    expect(fixture.componentInstance.methods()).toEqual([{ value: 'MOBILE_MONEY', label: 'Mobile Money' }]);
  });

  it('submits the payment declaration then reveals the proof upload step', () => {
    fixture.componentInstance.form.patchValue({
      transactionReference: 'MM-REF-1',
      payerPhone: '+2250700000000',
      payerName: 'Jean Payeur',
    });

    fixture.componentInstance.submitPayment();
    const req = httpMock.expectOne('/api/v1/orders/o1/payments');
    expect(req.request.body).toEqual({
      method: 'MOBILE_MONEY',
      receivedAmountXof: '100000',
      transactionReference: 'MM-REF-1',
      payerPhone: '+2250700000000',
      payerName: 'Jean Payeur',
    });
    req.flush({
      data: {
        id: 'p1',
        orderId: 'o1',
        method: 'MOBILE_MONEY',
        status: 'SUBMITTED',
        expectedAmountXof: '100000.00',
        receivedAmountXof: '100000.00',
        transactionReference: 'MM-REF-1',
        payerPhone: '+2250700000000',
        payerName: 'Jean Payeur',
        rejectionReason: null,
        proofs: [],
        submittedAt: '2026-01-01T10:05:00Z',
        confirmedAt: null,
        rejectedAt: null,
      },
      message: 'ok',
    });

    expect(fixture.componentInstance.payment()?.id).toBe('p1');
  });

  it('shows a safe-retry message on network failure, never a definitive failure message', () => {
    fixture.componentInstance.form.patchValue({
      transactionReference: 'MM-REF-1',
      payerPhone: '+2250700000000',
      payerName: 'Jean Payeur',
    });
    fixture.componentInstance.submitPayment();

    const req = httpMock.expectOne('/api/v1/orders/o1/payments');
    req.error(new ProgressEvent('error'), { status: 0, statusText: 'Unknown Error' });

    expect(fixture.componentInstance.errorMessage()).toContain('reessayer en toute securite');
    expect(fixture.componentInstance.submitting()).toBe(false);
  });

  it('reuses the same Idempotency-Key when retrying an unchanged submission after a failure', () => {
    fixture.componentInstance.form.patchValue({
      transactionReference: 'MM-REF-1',
      payerPhone: '+2250700000000',
      payerName: 'Jean Payeur',
    });

    fixture.componentInstance.submitPayment();
    const first = httpMock.expectOne('/api/v1/orders/o1/payments');
    const firstKey = first.request.headers.get('Idempotency-Key');
    first.error(new ProgressEvent('error'), { status: 0, statusText: 'Unknown Error' });

    fixture.componentInstance.submitPayment();
    const second = httpMock.expectOne('/api/v1/orders/o1/payments');
    expect(second.request.headers.get('Idempotency-Key')).toBe(firstKey);
    second.flush({
      data: {
        id: 'p1',
        orderId: 'o1',
        method: 'MOBILE_MONEY',
        status: 'SUBMITTED',
        expectedAmountXof: '100000.00',
        receivedAmountXof: '100000.00',
        transactionReference: 'MM-REF-1',
        payerPhone: '+2250700000000',
        payerName: 'Jean Payeur',
        rejectionReason: null,
        proofs: [],
        submittedAt: '2026-01-01T10:05:00Z',
        confirmedAt: null,
        rejectedAt: null,
      },
      message: 'ok',
    });
  });

  it('uses a new Idempotency-Key when the submitted reference changes (a different intent)', () => {
    fixture.componentInstance.form.patchValue({
      transactionReference: 'MM-REF-1',
      payerPhone: '+2250700000000',
      payerName: 'Jean Payeur',
    });
    fixture.componentInstance.submitPayment();
    const first = httpMock.expectOne('/api/v1/orders/o1/payments');
    const firstKey = first.request.headers.get('Idempotency-Key');
    first.flush(null, { status: 409, statusText: 'Conflict' });

    fixture.componentInstance.form.patchValue({ transactionReference: 'MM-REF-2' });
    fixture.componentInstance.submitPayment();
    const second = httpMock.expectOne('/api/v1/orders/o1/payments');
    expect(second.request.headers.get('Idempotency-Key')).not.toBe(firstKey);
    second.flush(null, { status: 409, statusText: 'Conflict' });
  });

  it('disables submission while a request is already in flight', () => {
    fixture.componentInstance.form.patchValue({
      transactionReference: 'MM-REF-1',
      payerPhone: '+2250700000000',
      payerName: 'Jean Payeur',
    });
    fixture.componentInstance.submitPayment();
    expect(fixture.componentInstance.submitting()).toBe(true);

    // Un second appel pendant que la premiere tentative est en cours ne doit declencher
    // aucune requete HTTP supplementaire (httpMock.verify() dans afterEach l'aurait signale).
    fixture.componentInstance.submitPayment();
    httpMock.expectOne('/api/v1/orders/o1/payments').flush(null, { status: 500, statusText: 'Error' });
  });

  it('uploads the proof for the payment just created', () => {
    fixture.componentInstance.payment.set({
      id: 'p1',
      orderId: 'o1',
      method: 'MOBILE_MONEY',
      status: 'SUBMITTED',
      expectedAmountXof: '100000.00',
      receivedAmountXof: '100000.00',
      transactionReference: 'MM-REF-1',
      payerPhone: '+2250700000000',
      payerName: 'Jean Payeur',
      rejectionReason: null,
      proofs: [],
      submittedAt: '2026-01-01T10:05:00Z',
      confirmedAt: null,
      rejectedAt: null,
    });

    const file = new File(['abc'], 'proof.jpg', { type: 'image/jpeg' });
    fixture.componentInstance.onFileSelected({ target: { files: [file] } } as unknown as Event);

    const req = httpMock.expectOne('/api/v1/payments/p1/proofs');
    expect(req.request.body instanceof FormData).toBe(true);
    req.flush({ data: { id: 'proof1', fileName: 'proof.jpg', contentType: 'image/jpeg', sizeBytes: 3, uploadedAt: '2026-01-01T10:06:00Z' }, message: 'ok' });

    expect(fixture.componentInstance.proofUploaded()).toBe(true);
  });
});
