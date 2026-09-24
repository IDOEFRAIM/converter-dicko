import { Component } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { QuoteCreatePage } from './quote-create.page';

@Component({ template: '' })
class DummyPage {}

/**
 * Verifie que le frontend transmet uniquement l'intention du client
 * (direction + un seul montant) et ne calcule ni ne fabrique jamais
 * lui-meme customerRate/fee/amountCny — ces valeurs viennent
 * exclusivement de la reponse backend.
 */
describe('QuoteCreatePage', () => {
  let fixture: ComponentFixture<QuoteCreatePage>;
  let httpMock: HttpTestingController;
  let router: Router;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [QuoteCreatePage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([{ path: '**', component: DummyPage }])],
    }).compileComponents();

    fixture = TestBed.createComponent(QuoteCreatePage);
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    fixture.detectChanges();
  });

  afterEach(() => httpMock.verify());

  it('sends only amountXof for SEND_XOF, with amountCny explicitly null', () => {
    fixture.componentInstance.setDirection('SEND_XOF');
    fixture.componentInstance.amountControl.setValue(100000);

    fixture.componentInstance.submit();

    const req = httpMock.expectOne('/api/v1/quotes');
    expect(req.request.body).toEqual({ direction: 'SEND_XOF', amountXof: '100000', amountCny: null });
    req.flush({ data: { id: 'q1' }, message: 'ok' });
  });

  it('sends only amountCny for RECEIVE_CNY, with amountXof explicitly null', () => {
    fixture.componentInstance.setDirection('RECEIVE_CNY');
    fixture.componentInstance.amountControl.setValue(500);

    fixture.componentInstance.submit();

    const req = httpMock.expectOne('/api/v1/quotes');
    expect(req.request.body).toEqual({ direction: 'RECEIVE_CNY', amountXof: null, amountCny: '500' });
    req.flush({ data: { id: 'q2' }, message: 'ok' });
  });

  it('shows the quote returned by the backend, then accepts it and moves to the beneficiary step', () => {
    const navigateSpy = vi.spyOn(router, 'navigate');
    fixture.componentInstance.setDirection('SEND_XOF');
    fixture.componentInstance.amountControl.setValue(100000);

    fixture.componentInstance.submit();
    httpMock.expectOne('/api/v1/quotes').flush({ data: { id: 'q1' }, message: 'ok' });
    expect(fixture.componentInstance.quote()?.id).toBe('q1');

    fixture.componentInstance.continueToOrder();
    httpMock.expectOne('/api/v1/quotes/q1/accept').flush({ data: { id: 'q1' }, message: 'ok' });

    expect(navigateSpy).toHaveBeenCalledWith(['/order/new'], { queryParams: { quoteId: 'q1' } });
  });

  it('does not call the API with an invalid amount', () => {
    fixture.componentInstance.amountControl.setValue(null);
    fixture.componentInstance.submit();
    httpMock.expectNone('/api/v1/quotes');
  });
});
