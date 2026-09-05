import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AdminCostRateService } from './admin-cost-rate.service';
import { PublishCostRateConfigurationRequest } from '../models/cost-rate.model';

describe('AdminCostRateService', () => {
  let service: AdminCostRateService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AdminCostRateService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('POSTs the publish request to /api/admin/cost-rates', () => {
    const body: PublishCostRateConfigurationRequest = {
      businessDate: '2026-09-04',
      rateXofUsd: '600',
      rateUsdCny: '7.1',
      feeXofUsdPercent: '0.01',
      feeUsdCnyFixedUsd: '2',
      referenceAmountXof: '1000000',
      note: null,
    };
    service.publish(body).subscribe();
    const req = httpMock.expectOne('/api/admin/cost-rates');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(body);
    req.flush({ data: { id: 'c1', breakEvenRate: '85.46' }, message: 'ok' });
  });

  it('reads the current config from /current', () => {
    service.current().subscribe();
    const req = httpMock.expectOne('/api/admin/cost-rates/current');
    expect(req.request.method).toBe('GET');
    req.flush({ data: {}, message: 'ok' });
  });

  it('paginates the history', () => {
    service.history(1, 30).subscribe();
    const req = httpMock.expectOne(
      (r) => r.url === '/api/admin/cost-rates' && r.params.get('page') === '1' && r.params.get('size') === '30',
    );
    req.flush({ data: { content: [], page: 1, size: 30, totalElements: 0, totalPages: 0, first: false, last: true }, message: 'ok' });
  });
});
