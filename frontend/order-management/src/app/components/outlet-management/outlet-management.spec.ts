import { ComponentFixture, TestBed } from '@angular/core/testing';

import { OutletManagement } from './outlet-management';

describe('OutletManagement', () => {
  let component: OutletManagement;
  let fixture: ComponentFixture<OutletManagement>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [OutletManagement],
    }).compileComponents();

    fixture = TestBed.createComponent(OutletManagement);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
