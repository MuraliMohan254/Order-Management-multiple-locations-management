import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TableManagementComponent } from './table-management';

describe('TableManagementComponent', () => {
  let component: TableManagementComponent;
  let fixture: ComponentFixture<TableManagementComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TableManagementComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(TableManagementComponent);
    component = fixture.componentInstance;
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
