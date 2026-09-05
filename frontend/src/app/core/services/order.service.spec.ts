import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { OrderService } from './order.service';
import { CreateOrderRequest } from '../models/order.model';

describe('OrderService', () => {
  let service: OrderService;
  let httpMock: HttpTestingController;

  const req: CreateOrderRequest = {
    quoteId: 'q1',
    beneficiary: null,
    supplierId: 's1',
    note: null,
    purpose: 'IMPORT_GOODS',
    purposeDetails: null,
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(OrderService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('adds the Idempotency-Key header on create only when a key is supplied', () => {
    service.create(req, 'idem-1').subscribe();
    const withKey = httpMock.expectOne('/api/v1/orders');
    expect(withKey.request.headers.get('Idempotency-Key')).toBe('idem-1');
    withKey.flush({ data: { id: 'o1' }, message: 'ok' });

    service.create(req).subscribe();
    const withoutKey = httpMock.expectOne('/api/v1/orders');
    expect(withoutKey.request.headers.has('Idempotency-Key')).toBe(false);
    withoutKey.flush({ data: { id: 'o2' }, message: 'ok' });
  });

  it('reads the aggregated tracking timeline', () => {
    service.getTracking('o1').subscribe();
    const r = httpMock.expectOne('/api/v1/orders/o1/tracking');
    expect(r.request.method).toBe('GET');
    r.flush({ data: { orderId: 'o1', currentStatus: 'PROCESSING', createdAt: '', completedAt: null, timeline: [] }, message: 'ok' });
  });

  it('requests the receipt as a blob', () => {
    service.downloadReceipt('o1').subscribe();
    const r = httpMock.expectOne('/api/v1/orders/o1/receipt');
    expect(r.request.responseType).toBe('blob');
    r.flush(new Blob(['%PDF-1.4']));
  });

  it('builds the enriched history query server-side (only set filters are sent)', () => {
    service
      .history({ status: 'COMPLETED', supplierId: 's1', from: '2026-01-01T00:00:00.000Z', page: 2, size: 15 })
      .subscribe();
    const r = httpMock.expectOne(
      (req) =>
        req.url === '/api/v1/orders/history' &&
        req.params.get('status') === 'COMPLETED' &&
        req.params.get('supplierId') === 's1' &&
        req.params.get('from') === '2026-01-01T00:00:00.000Z' &&
        req.params.get('page') === '2' &&
        req.params.get('size') === '15' &&
        !req.params.has('purpose') &&
        !req.params.has('to'),
    );
    expect(r.request.method).toBe('GET');
    r.flush({ data: { content: [], page: 2, size: 15, totalElements: 0, totalPages: 0, first: false, last: true }, message: 'ok' });
  });

  it('checks order feasibility via GET /feasibility?quoteId', () => {
    service.checkFeasibility('q1').subscribe();
    const r = httpMock.expectOne(
      (req) => req.url === '/api/v1/orders/feasibility' && req.params.get('quoteId') === 'q1',
    );
    expect(r.request.method).toBe('GET');
    r.flush({
      data: { quoteId: 'q1', amountCny: '1000', settlementReservationEnabled: true, sufficientLiquidity: false },
      message: 'ok',
    });
  });
});
