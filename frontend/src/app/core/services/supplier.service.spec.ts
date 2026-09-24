import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { SupplierService } from './supplier.service';
import { PayAgainRequest, SupplierRequest } from '../models/supplier.model';

describe('SupplierService', () => {
  let service: SupplierService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(SupplierService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('lists active suppliers by default with pagination params', () => {
    service.list().subscribe();
    const req = httpMock.expectOne(
      (r) => r.url === '/api/v1/suppliers' && r.params.get('page') === '0' && r.params.get('size') === '20',
    );
    expect(req.request.method).toBe('GET');
    req.flush({ data: { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, first: true, last: true }, message: 'ok' });
  });

  it('passes the status filter through', () => {
    service.list(0, 10, 'INACTIVE').subscribe();
    const req = httpMock.expectOne((r) => r.params.get('status') === 'INACTIVE');
    req.flush({ data: { content: [], page: 0, size: 10, totalElements: 0, totalPages: 0, first: true, last: true }, message: 'ok' });
  });

  it('maps favorite=true to the /favorite action and favorite=false to /unfavorite', () => {
    service.setFavorite('s1', true).subscribe();
    httpMock.expectOne('/api/v1/suppliers/s1/favorite').flush({ data: {}, message: 'ok' });

    service.setFavorite('s1', false).subscribe();
    httpMock.expectOne('/api/v1/suppliers/s1/unfavorite').flush({ data: {}, message: 'ok' });
  });

  it('sends the Idempotency-Key header on pay-again only when a key is provided', () => {
    const body: PayAgainRequest = { amountXof: '50000', purpose: null, purposeDetails: null };

    service.payAgain('s1', body, 'key-123').subscribe();
    const withKey = httpMock.expectOne('/api/v1/suppliers/s1/pay-again');
    expect(withKey.request.headers.get('Idempotency-Key')).toBe('key-123');
    withKey.flush({ data: { id: 'o1' }, message: 'ok' });

    service.payAgain('s1', body).subscribe();
    const withoutKey = httpMock.expectOne('/api/v1/suppliers/s1/pay-again');
    expect(withoutKey.request.headers.has('Idempotency-Key')).toBe(false);
    withoutKey.flush({ data: { id: 'o2' }, message: 'ok' });
  });

  it('POSTs the supplier payload on create', () => {
    const payload: SupplierRequest = {
      type: 'ALIPAY',
      displayName: 'Zhang Trading',
      accountNumber: 'zhang@alipay.com',
      accountName: null,
      legalName: null,
      phone: null,
      email: null,
      country: null,
      city: null,
      province: null,
      bankName: null,
      bankBranch: null,
      bankAddress: null,
      swiftCode: null,
      currency: 'CNY',
      purpose: 'IMPORT_GOODS',
      notes: null,
    };
    service.create(payload).subscribe();
    const req = httpMock.expectOne('/api/v1/suppliers');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(payload);
    req.flush({ data: { id: 's1' }, message: 'ok' });
  });
});
