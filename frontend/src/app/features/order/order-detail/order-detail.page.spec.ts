import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { OrderDetailPage } from './order-detail.page';

function baseOrder(status: string) {
  return {
    id: 'o1',
    reference: 'ORD-202608-000001',
    quoteId: 'q1',
    status,
    amountXof: '100000.00',
    amountCny: '1176.47',
    customerRate: '85.000000',
    feeXof: '0.00',
    netAmountXof: '100000.00',
    note: null as string | null,
    cancellationReason: null as string | null,
    rejectionReason: null as string | null,
    beneficiary: { type: 'ALIPAY', fullName: 'Zhang San', identifier: 'zhang@example.com', bankName: null, bankBranch: null },
    statusHistory: [{ fromStatus: null, toStatus: 'AWAITING_PAYMENT', changedBy: 'u1', reason: null, createdAt: '2026-01-01T10:00:00Z' }],
    createdAt: '2026-01-01T10:00:00Z',
    updatedAt: '2026-01-01T10:00:00Z',
    completedAt: null,
    cancelledAt: null,
  };
}

describe('OrderDetailPage', () => {
  let fixture: ComponentFixture<OrderDetailPage>;
  let httpMock: HttpTestingController;

  async function createWith(order: ReturnType<typeof baseOrder>) {
    await TestBed.configureTestingModule({
      imports: [OrderDetailPage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => 'o1' } } } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(OrderDetailPage);
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    httpMock.expectOne('/api/v1/orders/o1').flush({ data: order, message: 'ok' });
    fixture.detectChanges();
  }

  afterEach(() => httpMock.verify());

  it('displays the order reference, amounts and beneficiary', async () => {
    await createWith(baseOrder('AWAITING_PAYMENT'));
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('ORD-202608-000001');
    expect(text).toContain('Zhang San');
  });

  it('shows the payment-in-progress settlement narrative while PROCESSING', async () => {
    await createWith(baseOrder('PROCESSING'));
    expect(fixture.componentInstance.settlementView()?.stage).toBe('IN_PROGRESS');
    expect(fixture.nativeElement.textContent).toContain('Reglement en cours');
  });

  it('shows the completed settlement narrative once COMPLETED', async () => {
    await createWith(baseOrder('COMPLETED'));
    expect(fixture.componentInstance.settlementView()?.stage).toBe('COMPLETED');
  });

  it('shows the rejection reason for a REJECTED order', async () => {
    const order = baseOrder('REJECTED');
    order.rejectionReason = 'Preuve illisible';
    await createWith(order);
    expect(fixture.nativeElement.textContent).toContain('Preuve illisible');
  });

  it('shows a not-found message on a 404 (own resource missing or inaccessible)', async () => {
    await TestBed.configureTestingModule({
      imports: [OrderDetailPage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => 'o404' } } } },
      ],
    }).compileComponents();
    const notFoundFixture = TestBed.createComponent(OrderDetailPage);
    const mock = TestBed.inject(HttpTestingController);
    notFoundFixture.detectChanges();
    mock.expectOne('/api/v1/orders/o404').flush(
      { code: 'ORDER_NOT_FOUND', message: 'Ordre introuvable.' },
      { status: 404, statusText: 'Not Found' },
    );
    notFoundFixture.detectChanges();
    expect(notFoundFixture.nativeElement.textContent).toContain('Ordre introuvable.');
    mock.verify();
  });
});
