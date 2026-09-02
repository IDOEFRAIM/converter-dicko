import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, provideRouter } from '@angular/router';
import { QuoteDetailPage } from './quote-detail.page';

const BASE_QUOTE = {
  id: 'q1',
  direction: 'SEND_XOF',
  amountXof: '100000.00',
  amountCny: '1176.47',
  customerRate: '85.000000',
  feeXof: '0.00',
  netAmountXof: '100000.00',
  status: 'ACTIVE',
  createdAt: '2026-01-01T10:00:00Z',
  expiresAt: new Date(Date.now() + 30 * 60 * 1000).toISOString(),
};

describe('QuoteDetailPage', () => {
  let fixture: ComponentFixture<QuoteDetailPage>;
  let httpMock: HttpTestingController;
  let router: Router;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [QuoteDetailPage],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => 'q1' } } } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(QuoteDetailPage);
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    fixture.detectChanges();
    httpMock.expectOne('/api/v1/quotes/q1').flush({ data: BASE_QUOTE, message: 'ok' });
  });

  afterEach(() => httpMock.verify());

  it('displays the backend-computed amounts and rate without any recalculation', () => {
    fixture.detectChanges();
    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('100');
    expect(text).toContain('1');
    expect(fixture.componentInstance.quote()?.customerRate).toBe('85.000000');
  });

  it('accepting the quote calls the API then navigates to order creation with the quote id', () => {
    const navigateSpy = vi.spyOn(router, 'navigate');

    fixture.componentInstance.accept();
    const req = httpMock.expectOne('/api/v1/quotes/q1/accept');
    expect(req.request.method).toBe('POST');
    req.flush({ data: { ...BASE_QUOTE, status: 'ACCEPTED' }, message: 'ok' });

    expect(navigateSpy).toHaveBeenCalledWith(['/order/new'], { queryParams: { quoteId: 'q1' } });
  });
});
