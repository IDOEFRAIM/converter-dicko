import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AdminKycService } from './admin-kyc.service';

describe('AdminKycService', () => {
  let service: AdminKycService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AdminKycService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('paginates the pending submissions', () => {
    service.pending(1, 30).subscribe();
    const req = httpMock.expectOne(
      (r) =>
        r.url === '/api/admin/kyc/submissions' &&
        r.params.get('page') === '1' &&
        r.params.get('size') === '30',
    );
    expect(req.request.method).toBe('GET');
    req.flush({
      data: { content: [], page: 1, size: 30, totalElements: 0, totalPages: 0, first: false, last: true },
      message: 'ok',
    });
  });

  it('POSTs an approve', () => {
    service.approve('s1').subscribe();
    const req = httpMock.expectOne('/api/admin/kyc/submissions/s1/approve');
    expect(req.request.method).toBe('POST');
    req.flush({ data: null, message: 'ok' });
  });

  it('POSTs a reject with a reason', () => {
    service.reject('s1', { reason: 'Photo floue' }).subscribe();
    const req = httpMock.expectOne('/api/admin/kyc/submissions/s1/reject');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ reason: 'Photo floue' });
    req.flush({ data: null, message: 'ok' });
  });

  it('downloads a file as a blob', () => {
    service.downloadFile('s1', 'selfie').subscribe();
    const req = httpMock.expectOne('/api/admin/kyc/submissions/s1/files/selfie');
    expect(req.request.method).toBe('GET');
    expect(req.request.responseType).toBe('blob');
    req.flush(new Blob(['x']));
  });
});
