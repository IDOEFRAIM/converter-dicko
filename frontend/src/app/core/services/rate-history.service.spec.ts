import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { RateHistoryService } from './rate-history.service';

describe('RateHistoryService', () => {
  let service: RateHistoryService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(RateHistoryService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('requests the public rate history with paging and optional bounds', () => {
    service.history({ from: '2026-01-01T00:00:00.000Z', size: 60 }).subscribe();
    const req = httpMock.expectOne(
      (r) =>
        r.url === '/api/v1/rates/history' &&
        r.params.get('size') === '60' &&
        r.params.get('from') === '2026-01-01T00:00:00.000Z' &&
        !r.params.has('to'),
    );
    expect(req.request.method).toBe('GET');
    req.flush({ data: { content: [], page: 0, size: 60, totalElements: 0, totalPages: 0, first: true, last: true }, message: 'ok' });
  });

  it('creates a rate alert with only the target rate when nothing else is provided', () => {
    service.createAlert({ targetRate: '83.5', expiresAt: null }).subscribe();
    const req = httpMock.expectOne('/api/v1/rate-alerts');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ targetRate: '83.5', expiresAt: null });
    req.flush({ data: { id: 'a1' }, message: 'ok' });
  });

  it('cancels an alert via the /cancel action', () => {
    service.cancelAlert('a1').subscribe();
    const req = httpMock.expectOne('/api/v1/rate-alerts/a1/cancel');
    expect(req.request.method).toBe('POST');
    req.flush({ data: { id: 'a1', status: 'CANCELLED' }, message: 'ok' });
  });

  it('filters the alert list by status when provided', () => {
    service.listAlerts(0, 20, 'ACTIVE').subscribe();
    httpMock
      .expectOne((r) => r.url === '/api/v1/rate-alerts' && r.params.get('status') === 'ACTIVE')
      .flush({ data: { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, first: true, last: true }, message: 'ok' });
  });
});
