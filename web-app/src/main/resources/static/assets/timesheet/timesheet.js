class TimesheetManager {
    constructor() {
        this.rowCounter = this.getMaxRowId() + 1;
        this.saveTimer = null;
        this.projects = [];
        this.activities = [];
        this.init();
    }

    getMaxRowId() {
        const rows = document.querySelectorAll('.r-timesheet-row');
        let maxId = 0;
        rows.forEach(row => {
            const rowId = parseInt(row.getAttribute('data-row-id')) || 0;
            if (rowId > maxId) maxId = rowId;
        });
        return maxId;
    }

    init() {
        this.initDropdownData();
        this.setupEventListeners();
        this.setupEventHandlers();
    }

    async initDropdownData() {
        this.projects = window.timesheetData.projects || [];
        this.activities = window.timesheetData.activities || [];
    }

    setupEventHandlers() {
        document.addEventListener('change', (event) => {
            if (event.target.matches('.r-project-select')) {
                this.handleProjectChange(event.target);
            }
        });
    }

    handleProjectChange(selectElement) {
        const row = selectElement.closest('.r-timesheet-row');
        const activitySelect = row.querySelector('.r-activity-select');
        const projectId = selectElement.value;

        if (activitySelect && projectId) {
            this.updateActivityOptions(activitySelect, projectId);
        }
    }

    updateActivityOptions(activitySelect, projectId) {
        const filteredActivities = this.activities.filter(activity => 
            !activity.projectId || activity.projectId == projectId
        );

        activitySelect.innerHTML = '<wa-option value="">Select Activity</wa-option>';

        filteredActivities.forEach(activity => {
            const option = document.createElement('wa-option');
            option.value = activity.id;
            option.textContent = activity.name;
            option.setAttribute('data-billable', activity.billable);
            activitySelect.appendChild(option);
        });
    }


    enableSubmitButton() {
        const submitButton = document.getElementById('r-submit-button');
        const approvalIndicator = document.getElementById('r-approval-indicator');
        
        if (submitButton && submitButton.disabled && submitButton.classList.contains('submitted')) {

            submitButton.disabled = false;
            submitButton.classList.remove('submitted');
            submitButton.innerHTML = 'Submit';
            
            if (approvalIndicator) {
                approvalIndicator.style.display = 'none';
            }
        }
    }

    formatDate(date) {
        const year = date.getFullYear();
        const month = String(date.getMonth() + 1).padStart(2, '0');
        const day = String(date.getDate()).padStart(2, '0');
        return `${year}-${month}-${day}`;
    }

    setupEventListeners() {
        window.addNewRow = () => this.addNewRow();
        window.removeRow = (rowId) => this.removeRow(rowId);

        document.addEventListener('input', (e) => {
            if (e.target.classList.contains('r-hours-input')) {
                this.handleHoursChange(e.target);
            }
        });

        document.addEventListener('input', (e) => {
            if (e.target.classList.contains('r-comment-input')) {
                this.handleCommentChange(e.target);
            }
        });

        document.addEventListener('input', (e) => {
            if (e.target.matches('.r-hours-input, .r-comment-input, .r-project-input, .r-activity-input')) {
                this.enableSaveButton();
            }
        });

        document.addEventListener('change', (e) => {
            if (e.target.matches('.r-project-select, .r-activity-select')) {
                this.enableSaveButton();
            }
        });

        document.body.addEventListener('htmx:afterRequest', (evt) => {
            if (evt.detail.pathInfo.requestPath === '/htmx/timesheet/save') {

                if (evt.detail.xhr.status === 200) {
                    this.disableSaveButton();

                    if (evt.detail.target) {
                        htmx.process(evt.detail.target);
                    }
                } else {
                    console.error('HTMX save failed with status:', evt.detail.xhr.status);

                    if (savedIndicator) {
                        savedIndicator.setAttribute('variant', 'danger');
                        savedIndicator.innerHTML = '<wa-icon name="x"></wa-icon>Error';
                        savedIndicator.style.display = 'inline-flex';
                    }

                    if (saveButton) {
                        saveButton.disabled = false;
                        saveButton.setAttribute('variant', 'brand');
                    }
                }
            }
        });

    }

    handleHoursChange(input) {
        const value = parseFloat(input.value) || 0;
        
        // Validate hours (0-24)
        if (value < 0) {
            input.value = '0';
        } else if (value > 24) {
            input.value = '24';
        }
        
        this.calculateTotals();
        this.generateTimeReport();
        this.enableSaveButton();
    }

    handleCommentChange(input) {
        this.generateTimeReport();
        this.enableSaveButton();
    }

    calculateTotals() {
        const rows = document.querySelectorAll('.r-timesheet-row');
        const days = ['mon', 'tue', 'wed', 'thu', 'fri', 'sat', 'sun'];
        const dayTotals = { mon: 0, tue: 0, wed: 0, thu: 0, fri: 0, sat: 0, sun: 0 };
        let grandTotal = 0;

        // Calculate row totals and day totals
        rows.forEach((row, index) => {
            let rowTotal = 0;
            
            days.forEach(day => {
                // Use the sequential array index and square bracket notation to match actual input names
                const hoursInput = row.querySelector(`[name="rows[${index}].hours[${day}]"]`);
                if (hoursInput) {
                    const hours = parseFloat(hoursInput.value) || 0;
                    rowTotal += hours;
                    dayTotals[day] += hours;
                }
            });
            
            // Update row total display
            const rowTotalSpan = row.querySelector('.r-row-total');
            if (rowTotalSpan) {
                rowTotalSpan.textContent = rowTotal.toFixed(1);
            }
            
            grandTotal += rowTotal;
        });

        // Update day totals in footer
        days.forEach(day => {
            const dayTotalElement = document.getElementById(`r-total-${day}`);
            if (dayTotalElement) {
                dayTotalElement.textContent = dayTotals[day].toFixed(1);
            }
        });

        // Update grand total
        const grandTotalElement = document.getElementById('r-grand-total');
        if (grandTotalElement) {
            grandTotalElement.textContent = grandTotal.toFixed(1);
        }
    }

    addNewRow() {
        const tbody = document.getElementById('r-timesheet-rows');
        const rowId = this.rowCounter++;
        
        const existingRows = tbody.querySelectorAll('.r-timesheet-row');
        const arrayIndex = existingRows.length;
        
        const newRow = document.createElement('tr');
        newRow.className = 'r-timesheet-row';
        newRow.setAttribute('data-row-id', rowId);
        
        newRow.innerHTML = `
            <!-- Hidden fields for row data -->
            <input type="hidden" name="rows[${arrayIndex}].rowId" value="${rowId}"/>
            
            <td class="r-project-cell">
                ${this.createProjectDropdown(arrayIndex)}
            </td>
            <td class="r-activity-cell">
                ${this.createActivityDropdown(arrayIndex)}
            </td>
            <td class="r-day-cell">
                <wa-input type="number" size="small" min="0" max="24" step="0.25" 
                         name="rows[${arrayIndex}].hours[mon]" class="r-hours-input" 
                         data-day="mon" data-row="${rowId}" value="0"></wa-input>
                <wa-input size="small" placeholder="Comment" 
                         name="rows[${arrayIndex}].comments[mon]" class="r-comment-input" value=""></wa-input>
            </td>
            <td class="r-day-cell">
                <wa-input type="number" size="small" min="0" max="24" step="0.25" 
                         name="rows[${arrayIndex}].hours[tue]" class="r-hours-input" 
                         data-day="tue" data-row="${rowId}" value="0"></wa-input>
                <wa-input size="small" placeholder="Comment" 
                         name="rows[${arrayIndex}].comments[tue]" class="r-comment-input" value=""></wa-input>
            </td>
            <td class="r-day-cell">
                <wa-input type="number" size="small" min="0" max="24" step="0.25" 
                         name="rows[${arrayIndex}].hours[wed]" class="r-hours-input" 
                         data-day="wed" data-row="${rowId}" value="0"></wa-input>
                <wa-input size="small" placeholder="Comment" 
                         name="rows[${arrayIndex}].comments[wed]" class="r-comment-input" value=""></wa-input>
            </td>
            <td class="r-day-cell">
                <wa-input type="number" size="small" min="0" max="24" step="0.25" 
                         name="rows[${arrayIndex}].hours[thu]" class="r-hours-input" 
                         data-day="thu" data-row="${rowId}" value="0"></wa-input>
                <wa-input size="small" placeholder="Comment" 
                         name="rows[${arrayIndex}].comments[thu]" class="r-comment-input" value=""></wa-input>
            </td>
            <td class="r-day-cell">
                <wa-input type="number" size="small" min="0" max="24" step="0.25" 
                         name="rows[${arrayIndex}].hours[fri]" class="r-hours-input" 
                         data-day="fri" data-row="${rowId}" value="0"></wa-input>
                <wa-input size="small" placeholder="Comment" 
                         name="rows[${arrayIndex}].comments[fri]" class="r-comment-input" value=""></wa-input>
            </td>
            <td class="r-day-cell">
                <wa-input type="number" size="small" min="0" max="24" step="0.25" 
                         name="rows[${arrayIndex}].hours[sat]" class="r-hours-input" 
                         data-day="sat" data-row="${rowId}" value="0"></wa-input>
                <wa-input size="small" placeholder="Comment" 
                         name="rows[${arrayIndex}].comments[sat]" class="r-comment-input" value=""></wa-input>
            </td>
            <td class="r-day-cell">
                <wa-input type="number" size="small" min="0" max="24" step="0.25" 
                         name="rows[${arrayIndex}].hours[sun]" class="r-hours-input" 
                         data-day="sun" data-row="${rowId}" value="0"></wa-input>
                <wa-input size="small" placeholder="Comment" 
                         name="rows[${arrayIndex}].comments[sun]" class="r-comment-input" value=""></wa-input>
            </td>
            <td class="r-total-cell">
                <span class="r-row-total" data-row="${rowId}">0.0</span>
            </td>
            <td class="r-remain-cell">
                <wa-button size="small" variant="danger" appearance="outlined" 
                          onclick="removeRow(${rowId})" type="button">
                    <wa-icon name="x"></wa-icon>
                </wa-button>
            </td>
        `;
        
        tbody.appendChild(newRow);
        
        this.enableSaveButton();
        
        const firstInput = newRow.querySelector('.r-project-select');
        if (firstInput) {
            firstInput.focus();
        }
    }

    async removeRow(rowId) {
        const row = document.querySelector(`[data-row-id="${rowId}"]`);
        if (!row) return;

        row.remove();
        this.disableSaveButton();
        this.calculateTotals();
        this.generateTimeReport();
    }

    generateTimeReport() {
        const tbody = document.getElementById('r-time-report-rows');
        if (!tbody) return;
        
        tbody.innerHTML = '';
        
        const rows = document.querySelectorAll('.r-timesheet-row');
        const days = ['mon', 'tue', 'wed', 'thu', 'fri', 'sat', 'sun'];
        
        // Get week start from the hidden input field
        const weekStartInput = document.querySelector('input[name="weekStart"]');
        if (!weekStartInput) return;
        
        const weekStartDate = new Date(weekStartInput.value + 'T00:00:00');
        
        rows.forEach((row, index) => {
            const projectSelect = row.querySelector('.r-project-select');
            const activitySelect = row.querySelector('.r-activity-select');
            
            // Get selected text from wa-select components using their displayText property
            let projectText = '';
            let activityText = '';
            
            if (projectSelect && projectSelect.value) {
                // For wa-select, we can use displayText property or the selected option's textContent
                projectText = projectSelect.displayText || '';
                // Fallback to finding the selected option if displayText is not available
                if (!projectText) {
                    const selectedOption = projectSelect.querySelector(`wa-option[value="${projectSelect.value}"]`);
                    projectText = selectedOption ? selectedOption.textContent.trim() : '';
                }
            }
            
            if (activitySelect && activitySelect.value) {
                activityText = activitySelect.displayText || '';
                if (!activityText) {
                    const selectedOption = activitySelect.querySelector(`wa-option[value="${activitySelect.value}"]`);
                    activityText = selectedOption ? selectedOption.textContent.trim() : '';
                }
            }
            
            if (!projectText || projectText === 'Select Project') return;
            
            days.forEach((day, dayIndex) => {
                // Use the sequential array index and square bracket notation to match actual input names
                const hoursInput = row.querySelector(`[name="rows[${index}].hours[${day}]"]`);
                const commentInput = row.querySelector(`[name="rows[${index}].comments[${day}]"]`);
                
                const hours = hoursInput ? parseFloat(hoursInput.value) || 0 : 0;
                const comment = commentInput ? commentInput.value : '';
                
                if (hours > 0) {
                    const date = new Date(weekStartDate);
                    date.setDate(date.getDate() + dayIndex);
                    
                    const reportRow = document.createElement('tr');
                    reportRow.innerHTML = `
                        <td>${this.formatDate(date)}</td>
                        <td>${projectText}${activityText && activityText !== 'Select Activity' ? ' / ' + activityText : ''}</td>
                        <td>${hours}</td>
                        <td>${comment || '-'}</td>
                    `;
                    
                    tbody.appendChild(reportRow);
                }
            });
        });
        
        // Show message if no entries
        if (tbody.children.length === 0) {
            const emptyRow = document.createElement('tr');
            emptyRow.innerHTML = `
                <td colspan="4" style="text-align: center; color: var(--wa-color-gray-50); font-style: italic;">
                    No time entries for this week
                </td>
            `;
            tbody.appendChild(emptyRow);
        }
    }

    enableSaveButton() {
        const saveButton = document.getElementById('r-save-button');
        const savedIndicator = document.getElementById('r-saved-indicator');
        
        if (saveButton) {
            saveButton.disabled = false;
            saveButton.setAttribute('variant', 'brand');
        }
        
        if (savedIndicator) {
            savedIndicator.setAttribute('variant', 'warning');
            savedIndicator.innerHTML = '<wa-icon name="edit"></wa-icon>Unsaved changes';
        }
        
        this.enableSubmitButton();
    }

    disableSaveButton() {
        const saveButton = document.getElementById('r-save-button');
        const savedIndicator = document.getElementById('r-saved-indicator');
        
        if (saveButton) {
            saveButton.disabled = true;
            saveButton.setAttribute('variant', 'default');
        }

        if (savedIndicator) {
            savedIndicator.style.display = 'inline-flex';
            savedIndicator.setAttribute('variant', 'success');
            const span = savedIndicator.textContent ? savedIndicator : savedIndicator.querySelector('span');
            if (span) span.textContent = 'Saved';
        }
    }


    createProjectDropdown(arrayIndex) {
        const options = this.projects.map(project => 
            `<wa-option value="${project.id}">${project.name}</wa-option>`
        ).join('');
        
        return `
            <wa-select size="small" name="rows[${arrayIndex}].projectId" class="r-project-select" 
                       value="">
                <wa-option value="" selected>Select Project</wa-option>
                ${options}
            </wa-select>
        `;
    }

    createActivityDropdown(arrayIndex) {
        const options = this.activities.map(activity => 
            `<wa-option value="${activity.id}" data-billable="${activity.billable}">${activity.name}</wa-option>`
        ).join('');
        
        return `
            <wa-select size="small" name="rows[${arrayIndex}].activityId" class="r-activity-select" value="">
                <wa-option value="" selected>Select Activity</wa-option>
                ${options}
            </wa-select>
        `;
    }
}

function initTimesheet() {
    if (typeof Temporal === 'undefined') {
        console.warn('Temporal API not available, using Date fallback');
        
        window.Temporal = {
            Now: {
                plainDateISO() {
                    const now = new Date();
                    return {
                        year: now.getFullYear(),
                        month: now.getMonth() + 1,
                        day: now.getDate(),
                        dayOfWeek: now.getDay() === 0 ? 7 : now.getDay(),
                        dayOfYear: Math.floor((now - new Date(now.getFullYear(), 0, 0)) / 86400000),
                        
                        subtract(options) {
                            const newDate = new Date(now.getTime() - (options.days || 0) * 86400000);
                            return Temporal.Now.plainDateISO.call({ getTime: () => newDate.getTime() });
                        },
                        
                        add(options) {
                            const newDate = new Date(now.getTime() + (options.days || 0) * 86400000);
                            return Temporal.Now.plainDateISO.call({ getTime: () => newDate.getTime() });
                        },
                        
                        with(options) {
                            const newDate = new Date(now);
                            if (options.month) newDate.setMonth(options.month - 1);
                            if (options.day) newDate.setDate(options.day);
                            return Temporal.Now.plainDateISO.call({ getTime: () => newDate.getTime() });
                        },
                        
                        toString() {
                            return now.toISOString().split('T')[0];
                        }
                    };
                }
            }
        };
    }
    
    window.timesheetManager = new TimesheetManager();
}

// Add this to your timesheet.js file
function checkUnsavedChanges(event) {
    const saveButton = document.getElementById('r-save-button');
    if (saveButton && !saveButton.disabled) {
        const confirmed = confirm('You have unsaved changes. Do you want to submit without saving?');
        if (!confirmed) {
            event.preventDefault();
            return false;
        }
    }
    return true;
}

if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initTimesheet);
} else {
    initTimesheet();
}