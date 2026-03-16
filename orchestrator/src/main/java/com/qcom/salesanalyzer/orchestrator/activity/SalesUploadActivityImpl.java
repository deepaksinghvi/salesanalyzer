package com.qcom.salesanalyzer.orchestrator.activity;

import com.opencsv.CSVReader;
import com.qcom.salesanalyzer.orchestrator.entity.DimCategory;
import com.qcom.salesanalyzer.orchestrator.entity.DimLocation;
import com.qcom.salesanalyzer.orchestrator.entity.FactSalesDaily;
import com.qcom.salesanalyzer.orchestrator.entity.UploadJob;
import com.qcom.salesanalyzer.orchestrator.repository.DimCategoryRepository;
import com.qcom.salesanalyzer.orchestrator.repository.DimLocationRepository;
import com.qcom.salesanalyzer.orchestrator.repository.FactSalesDailyRepository;
import com.qcom.salesanalyzer.orchestrator.repository.UploadJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.FileReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class SalesUploadActivityImpl implements SalesUploadActivity {

    private final UploadJobRepository uploadJobRepository;
    private final DimCategoryRepository categoryRepository;
    private final DimLocationRepository locationRepository;
    private final FactSalesDailyRepository factSalesDailyRepository;

    @Override
    @Transactional
    public void updateJobStatus(String jobId, String status, String errorMessage) {
        uploadJobRepository.findById(UUID.fromString(jobId)).ifPresent(job -> {
            job.setStatus(status);
            job.setErrorMessage(errorMessage);
            uploadJobRepository.save(job);
        });
    }

    @Override
    @Transactional
    public int parseCsvAndInsert(String jobId, String tenantId, String filePath) {
        UUID tenantUuid = UUID.fromString(tenantId);
        List<FactSalesDaily> rows = new ArrayList<>();

        try (CSVReader reader = new CSVReader(new FileReader(filePath))) {
            String[] headers = reader.readNext(); // skip header
            if (headers == null) return 0;

            String[] line;
            while ((line = reader.readNext()) != null) {
                if (line.length < 7) continue;

                // CSV columns: tenant_id, transaction_date, category_name, city, region, total_revenue, units_sold
                LocalDate date = LocalDate.parse(line[1].trim());
                String categoryName = line[2].trim();
                String city = line[3].trim();
                String region = line[4].trim();
                BigDecimal revenue = new BigDecimal(line[5].trim());
                int units = Integer.parseInt(line[6].trim());

                // Resolve or create category
                DimCategory category = categoryRepository
                        .findByTenantIdAndName(tenantUuid, categoryName)
                        .orElseGet(() -> categoryRepository.save(
                                DimCategory.builder().tenantId(tenantUuid).name(categoryName).build()));

                // Resolve or create location
                DimLocation location = locationRepository
                        .findByCityAndRegion(city, region)
                        .orElseGet(() -> locationRepository.save(
                                DimLocation.builder().city(city).region(region).build()));

                rows.add(FactSalesDaily.builder()
                        .transactionDate(date)
                        .tenantId(tenantUuid)
                        .categoryId(category.getCategoryId())
                        .locationId(location.getLocationId())
                        .amount(revenue)
                        .unitsSold(units)
                        .isForecast(false)
                        .build());
            }

            factSalesDailyRepository.saveAll(rows);
            log.info("Inserted {} rows for tenant {}", rows.size(), tenantId);

            // Update rows_processed on job
            uploadJobRepository.findById(UUID.fromString(jobId)).ifPresent(job -> {
                job.setRowsProcessed(rows.size());
                uploadJobRepository.save(job);
            });

            return rows.size();
        } catch (Exception e) {
            log.error("Error parsing CSV for job {}: {}", jobId, e.getMessage(), e);
            throw new RuntimeException("CSV parsing failed: " + e.getMessage(), e);
        }
    }
}
