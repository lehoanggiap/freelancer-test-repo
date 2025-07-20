console.log('=== Dashboard JavaScript Loaded ===');

// Initialize chart when DOM is loaded
document.addEventListener('DOMContentLoaded', function() {
    console.log('DOM loaded, initializing dashboard...');
    
    // Get chart data from global variable (set by Thymeleaf)
    const monthlyTrendsData = window.monthlyTrendsData || [];
    
    // Debug logging
    console.log('Monthly trends data:', monthlyTrendsData);
    console.log('Type of monthlyTrendsData:', typeof monthlyTrendsData);
    console.log('Length of monthlyTrendsData:', monthlyTrendsData ? monthlyTrendsData.length : 'undefined');
    
    const canvas = document.getElementById('monthlyTrendsChart');
    if (!canvas) {
        console.error('Canvas element not found!');
        return;
    }
    
    const ctx = canvas.getContext('2d');
    if (!ctx) {
        console.error('Could not get 2D context!');
        return;
    }
    
    console.log('Canvas and context obtained successfully');
    
    const labels = monthlyTrendsData.map(item => item.month + ' ' + item.year);
    const revenueData = monthlyTrendsData.map(item => item.revenue);
    const expenseData = monthlyTrendsData.map(item => item.expenses);
    const netIncomeData = monthlyTrendsData.map(item => item.netIncome);
    
    console.log('Processed data:', { labels, revenueData, expenseData, netIncomeData });
    
    // Check if Chart is available
    if (typeof Chart === 'undefined') {
        console.error('Chart.js is not loaded!');
        return;
    }
    
    console.log('Chart.js is available, creating chart...');
    
    // Check if we have data
    if (!monthlyTrendsData || monthlyTrendsData.length === 0) {
        console.log('No data available, showing fallback');
        document.getElementById('monthlyTrendsChart').style.display = 'none';
        document.getElementById('chartFallback').style.display = 'block';
        return;
    }
    
    // Enhanced chart configuration
    new Chart(ctx, {
        type: 'line',
        data: {
            labels: labels,
            datasets: [{
                label: 'Revenue',
                data: revenueData,
                borderColor: '#22c55e',
                backgroundColor: 'rgba(34, 197, 94, 0.1)',
                borderWidth: 3,
                tension: 0.4,
                fill: true,
                pointBackgroundColor: '#22c55e',
                pointBorderColor: '#ffffff',
                pointBorderWidth: 2,
                pointRadius: 6,
                pointHoverRadius: 8
            }, {
                label: 'Expenses',
                data: expenseData,
                borderColor: '#ef4444',
                backgroundColor: 'rgba(239, 68, 68, 0.1)',
                borderWidth: 3,
                tension: 0.4,
                fill: true,
                pointBackgroundColor: '#ef4444',
                pointBorderColor: '#ffffff',
                pointBorderWidth: 2,
                pointRadius: 6,
                pointHoverRadius: 8
            }, {
                label: 'Net Income',
                data: netIncomeData,
                borderColor: '#3b82f6',
                backgroundColor: 'rgba(59, 130, 246, 0.1)',
                borderWidth: 3,
                tension: 0.4,
                fill: true,
                pointBackgroundColor: '#3b82f6',
                pointBorderColor: '#ffffff',
                pointBorderWidth: 2,
                pointRadius: 6,
                pointHoverRadius: 8
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            interaction: {
                intersect: false,
                mode: 'index'
            },
            plugins: {
                legend: {
                    position: 'top',
                    labels: {
                        usePointStyle: true,
                        padding: 20,
                        font: {
                            size: 12,
                            weight: '600'
                        }
                    }
                },
                tooltip: {
                    backgroundColor: 'rgba(0, 0, 0, 0.8)',
                    titleColor: '#ffffff',
                    bodyColor: '#ffffff',
                    borderColor: 'rgba(255, 255, 255, 0.1)',
                    borderWidth: 1,
                    cornerRadius: 8,
                    displayColors: true,
                    callbacks: {
                        label: function(context) {
                            return context.dataset.label + ': $' + context.parsed.y.toLocaleString();
                        }
                    }
                }
            },
            scales: {
                x: {
                    grid: {
                        display: false
                    },
                    ticks: {
                        font: {
                            size: 11
                        }
                    }
                },
                y: {
                    beginAtZero: true,
                    grid: {
                        color: 'rgba(0, 0, 0, 0.05)'
                    },
                    ticks: {
                        font: {
                            size: 11
                        },
                        callback: function(value) {
                            return '$' + value.toLocaleString();
                        }
                    }
                }
            },
            elements: {
                point: {
                    hoverBackgroundColor: '#ffffff'
                }
            }
        }
    });
}); 