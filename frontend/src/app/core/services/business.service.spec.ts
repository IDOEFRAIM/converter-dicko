import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { BusinessService } from './business.service';
import { UpsertBusinessProfileRequest } from '../models/business.model';

describe('BusinessService', () => {
  let service: BusinessService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(BusinessService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('GETs the singleton business profile', () => {
    service.getProfile().subscribe();
    const req = httpMock.expectOne('/api/v1/business-profile');
    expect(req.request.method).toBe('GET');
    req.flush({ data: {}, message: 'ok' });
  });

  it('upserts the profile via PUT with no userId in the body', () => {
    const body: UpsertBusinessProfileRequest = {
      businessName: 'Zhang Trading',
      businessType: 'IMPORTER',
      registrationNumber: null,
      country: 'Burkina Faso',
      city: null,
      address: null,
    };
    service.upsertProfile(body).subscribe();
    const req = httpMock.expectOne('/api/v1/business-profile');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(body);
    expect('userId' in (req.request.body as object)).toBe(false);
    req.flush({ data: { id: 'b1' }, message: 'ok' });
  });

  it('omits from/to on the summary when not provided', () => {
    service.paymentsSummary().subscribe();
    const req = httpMock.expectOne(
      (r) => r.url === '/api/v1/business/payments/summary' && !r.params.has('from') && !r.params.has('to'),
    );
    req.flush({ data: {}, message: 'ok' });
  });

  it('passes from/to on the summary when provided', () => {
    service.paymentsSummary('2026-01-01T00:00:00.000Z', '2026-02-01T00:00:00.000Z').subscribe();
    const req = httpMock.expectOne(
      (r) => r.params.get('from') === '2026-01-01T00:00:00.000Z' && r.params.get('to') === '2026-02-01T00:00:00.000Z',
    );
    req.flush({ data: {}, message: 'ok' });
  });
});
