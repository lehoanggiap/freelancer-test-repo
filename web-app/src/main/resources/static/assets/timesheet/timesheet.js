class TimesheetManager {
    static CONSTANTS = {
        MAX_HOURS_PER_DAY: 24,
        MIN_HOURS_PER_DAY: 0,
        SELECTORS: {
            PROJECT_SELECT: '.r-project-select',
            ACTIVITY_SELECT: '.r-activity-select',
            HOURS_INPUT: '.r-hours-input',
            COMMENT_INPUT: '.r-comment-input',
            PROJECT_INPUT: '.r-project-input',
            ACTIVITY_INPUT: '.r-activity-input',
            TIMESHEET_ROW: '.r-timesheet-row',
            SAVE_BUTTON: '#r-save-button',
            SAVED_INDICATOR: '#r-saved-indicator',
            SUBMIT_BUTTON: '#r-submit-button',
            APPROVAL_INDICATOR: '#r-approval-indicator'
        },
        HTMX_PATHS: {
            SAVE: '/htmx/timesheet/save'
        },
        VARIANTS: {
            BRAND: 'brand',
            DEFAULT: 'default',
            SUCCESS: 'success',
            WARNING: 'warning',
            DANGER: 'danger'
        }
    };

    constructor() {
        this.projects = [];
        this.activities = [];
        this.init();
    }

    init() {
        this.initializeDropdownData();
        this.setupAllEventListeners();
    }

    initializeDropdownData() {
        this.projects = window.timesheetData?.projects || [];
        this.activities = window.timesheetData?.activities || [];
    }

    setupAllEventListeners() {
        this.setupProjectChangeHandlers();
        this.setupInputHandlers();
        this.setupFormChangeHandlers();
        this.setupHTMXHandlers();
        this.exposeGlobalMethods();
    }

    setupProjectChangeHandlers() {
        document.addEventListener('change', (event) => {
            if (event.target.matches(TimesheetManager.CONSTANTS.SELECTORS.PROJECT_SELECT)) {
                this.handleProjectChange(event.target);
            }
        });
    }

    setupInputHandlers() {
        document.addEventListener('input', (event) => {
            if (event.target.classList.contains('r-hours-input')) {
                this.handleHoursChange(event.target);
            } else if (event.target.classList.contains('r-comment-input')) {
                this.handleCommentChange(event.target);
            } else if (this.isFormInput(event.target)) {
                this.enableSaveButton();
            }
        });
    }

    setupFormChangeHandlers() {
        document.addEventListener('change', (event) => {
            if (this.isFormSelect(event.target)) {
                this.enableSaveButton();
            }
        });
    }

    setupHTMXHandlers() {
        document.body.addEventListener('htmx:afterRequest', (event) => {
            this.handleHTMXResponse(event);
        });
    }

    isFormInput(target) {
        const inputSelectors = [
            TimesheetManager.CONSTANTS.SELECTORS.HOURS_INPUT,
            TimesheetManager.CONSTANTS.SELECTORS.COMMENT_INPUT,
            TimesheetManager.CONSTANTS.SELECTORS.PROJECT_INPUT,
            TimesheetManager.CONSTANTS.SELECTORS.ACTIVITY_INPUT
        ];
        return inputSelectors.some(selector => target.matches(selector));
    }

    isFormSelect(target) {
        const selectSelectors = [
            TimesheetManager.CONSTANTS.SELECTORS.PROJECT_SELECT,
            TimesheetManager.CONSTANTS.SELECTORS.ACTIVITY_SELECT
        ];
        return selectSelectors.some(selector => target.matches(selector));
    }

    handleProjectChange(selectElement) {
        const row = this.findRowContainer(selectElement);
        const activitySelect = this.findActivitySelect(row);
        const projectId = selectElement.value;

        if (activitySelect && projectId) {
            this.updateActivityOptions(activitySelect, projectId);
        }
    }

    findRowContainer(element) {
        return element.closest(TimesheetManager.CONSTANTS.SELECTORS.TIMESHEET_ROW);
    }

    findActivitySelect(row) {
        return row?.querySelector(TimesheetManager.CONSTANTS.SELECTORS.ACTIVITY_SELECT);
    }

    updateActivityOptions(activitySelect, projectId) {
        const filteredActivities = this.getActivitiesForProject(projectId);
        this.renderActivityOptions(activitySelect, filteredActivities);
    }

    getActivitiesForProject(projectId) {
        return this.activities.filter(activity => 
            !activity.projectId || activity.projectId == projectId
        );
    }

    renderActivityOptions(activitySelect, activities) {
        activitySelect.innerHTML = '<wa-option value="">Select Activity</wa-option>';
        
        activities.forEach(activity => {
            const option = this.createActivityOption(activity);
            activitySelect.appendChild(option);
        });
    }

    createActivityOption(activity) {
        const option = document.createElement('wa-option');
        option.value = activity.id;
        option.textContent = activity.name;
        option.setAttribute('data-billable', activity.billable);
        return option;
    }

    handleHoursChange(input) {
        const validatedValue = this.validateHoursInput(input);
        input.value = validatedValue;
        
        this.enableSaveButton();
    }

    validateHoursInput(input) {
        const value = parseFloat(input.value) || TimesheetManager.CONSTANTS.MIN_HOURS_PER_DAY;
        
        if (value < TimesheetManager.CONSTANTS.MIN_HOURS_PER_DAY) {
            return TimesheetManager.CONSTANTS.MIN_HOURS_PER_DAY.toString();
        }
        
        if (value > TimesheetManager.CONSTANTS.MAX_HOURS_PER_DAY) {
            return TimesheetManager.CONSTANTS.MAX_HOURS_PER_DAY.toString();
        }
        
        return value.toString();
    }

    handleCommentChange() {
        this.enableSaveButton();
    }

    enableSaveButton() {
        this.updateSaveButtonState(false, TimesheetManager.CONSTANTS.VARIANTS.BRAND);
        this.updateSavedIndicatorState(TimesheetManager.CONSTANTS.VARIANTS.WARNING, '<wa-icon name="edit"></wa-icon>Unsaved changes');
        this.enableSubmitButton();
    }

    disableSaveButton() {
        this.updateSaveButtonState(true, TimesheetManager.CONSTANTS.VARIANTS.DEFAULT);
        this.updateSavedIndicatorState(TimesheetManager.CONSTANTS.VARIANTS.SUCCESS, 'Saved', true);
    }

    updateSaveButtonState(disabled, variant) {
        const saveButton = this.getElement(TimesheetManager.CONSTANTS.SELECTORS.SAVE_BUTTON);
        
        if (saveButton) {
            saveButton.disabled = disabled;
            saveButton.setAttribute('variant', variant);
        }
    }

    updateSavedIndicatorState(variant, content, shouldShow = false) {
        const savedIndicator = this.getElement(TimesheetManager.CONSTANTS.SELECTORS.SAVED_INDICATOR);
        
        if (savedIndicator) {
            savedIndicator.setAttribute('variant', variant);
            savedIndicator.innerHTML = content;
            
            if (shouldShow) {
                savedIndicator.style.display = 'inline-flex';
            }
        }
    }

    enableSubmitButton() {
        const submitButton = this.getElement(TimesheetManager.CONSTANTS.SELECTORS.SUBMIT_BUTTON);
        const approvalIndicator = this.getElement(TimesheetManager.CONSTANTS.SELECTORS.APPROVAL_INDICATOR);
        
        if (this.shouldEnableSubmitButton(submitButton)) {
            this.resetSubmitButtonState(submitButton, approvalIndicator);
        }
    }

    shouldEnableSubmitButton(submitButton) {
        return submitButton && 
               submitButton.disabled && 
               submitButton.classList.contains('submitted');
    }

    resetSubmitButtonState(submitButton, approvalIndicator) {
        submitButton.disabled = false;
        submitButton.classList.remove('submitted');
        submitButton.innerHTML = 'Submit';
        
        if (approvalIndicator) {
            approvalIndicator.style.display = 'none';
        }
    }

    handleHTMXResponse(event) {
        if (this.isSaveRequest(event)) {
            this.processSaveResponse(event);
        }
    }

    isSaveRequest(event) {
        return event.detail.pathInfo.requestPath === TimesheetManager.CONSTANTS.HTMX_PATHS.SAVE;
    }

    processSaveResponse(event) {
        if (this.isSuccessfulResponse(event)) {
            this.handleSuccessfulSave(event);
        } else {
            this.handleFailedSave(event);
        }
    }

    isSuccessfulResponse(event) {
        return event.detail.xhr.status === 200;
    }

    handleSuccessfulSave(event) {
        this.disableSaveButton();
        
        if (event.detail.target) {
            htmx.process(event.detail.target);
        }
    }

    handleFailedSave(event) {
        console.error('HTMX save failed with status:', event.detail.xhr.status);
        this.showSaveError();
    }

    showSaveError() {
        const savedIndicator = this.getElement(TimesheetManager.CONSTANTS.SELECTORS.SAVED_INDICATOR);
        
        if (savedIndicator) {
            savedIndicator.setAttribute('variant', TimesheetManager.CONSTANTS.VARIANTS.DANGER);
            savedIndicator.innerHTML = '<wa-icon name="x"></wa-icon>Error';
            savedIndicator.style.display = 'inline-flex';
        }
    }

    getElement(selector) {
        return document.querySelector(selector);
    }
}

function initializeTimesheet() {
    window.timesheetManager = new TimesheetManager();
}

if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initializeTimesheet);
} else {
    initializeTimesheet();
}