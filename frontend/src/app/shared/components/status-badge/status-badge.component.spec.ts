import { ComponentFixture, TestBed } from '@angular/core/testing';
import { StatusBadgeComponent } from './status-badge.component';

describe('StatusBadgeComponent', () => {
  let fixture: ComponentFixture<StatusBadgeComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [StatusBadgeComponent] }).compileComponents();
    fixture = TestBed.createComponent(StatusBadgeComponent);
  });

  it('renders a positive tone for a CONFIRMED payment', () => {
    fixture.componentRef.setInput('status', 'CONFIRMED');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Confirme');
    expect(fixture.nativeElement.querySelector('.status-badge--positive')).toBeTruthy();
  });

  it('renders a negative tone for a REJECTED payment', () => {
    fixture.componentRef.setInput('status', 'REJECTED');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Rejete');
    expect(fixture.nativeElement.querySelector('.status-badge--negative')).toBeTruthy();
  });

  it('renders a progress tone for a submitted payment awaiting review', () => {
    fixture.componentRef.setInput('status', 'SUBMITTED');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.status-badge--progress')).toBeTruthy();
  });

  it('falls back to the raw code for an unknown status, rather than failing', () => {
    fixture.componentRef.setInput('status', 'SOMETHING_NEW');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('SOMETHING_NEW');
  });
});
