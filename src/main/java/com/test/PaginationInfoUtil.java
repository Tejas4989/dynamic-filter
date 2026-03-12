package com.test;

import com.aig.gi.talbot.merged.model.neo.generated.web.rest.model.PaginationInfo;
import com.aig.gi.talbot.merged.model.neo.generated.web.rest.model.PaginationInfoSort;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaginationInfoUtil {

  private final SortUtil sortUtil;

  public PaginationInfo buildPaginationInfo(
      PaginationRequest request, int currentRecordCount, int total) {
    int currentPage = (request.offset() / request.limit()) + 1;
    int totalPages = (int) Math.ceil((double) total / request.limit());

    List<PaginationInfoSort> sortList = parseSortParam(request.sortRequest());
    return PaginationInfo.builder()
        .currentPage(currentPage)
        .pageSize(request.limit())
        .currentRecordCount(currentRecordCount)
        .totalPages(totalPages)
        .totalRecordCount(total)
        .sort(sortList)
        .build();
  }

  // Example input: createdAt:DESC,username:ASC
  public List<PaginationInfoSort> parseSortParam(String sortParam) {
    if (sortParam == null || sortParam.isBlank()) {
      return List.of();
    }

    return Arrays.stream(sortParam.split(","))
        .map(
            entry -> {
              String[] parts = entry.split(":");
              Optional<SortItem> sortItem = sortUtil.extractSortItem(parts);
              return sortItem
                  .map(
                      item ->
                          PaginationInfoSort.builder()
                              .field(item.field())
                              .order(PaginationInfoSort.OrderEnum.fromValue(item.direction()))
                              .build())
                  .orElse(null);
            })
        .filter(Objects::nonNull)
        .toList();
  }
}
