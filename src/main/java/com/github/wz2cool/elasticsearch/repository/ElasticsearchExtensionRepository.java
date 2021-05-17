package com.github.wz2cool.elasticsearch.repository;

import com.github.wz2cool.elasticsearch.helper.LogicPagingHelper;
import com.github.wz2cool.elasticsearch.model.LogicPagingResult;
import com.github.wz2cool.elasticsearch.model.SortDescriptor;
import com.github.wz2cool.elasticsearch.model.UpDown;

import com.github.wz2cool.elasticsearch.query.LogicPagingQuery;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.sort.SortBuilders;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.aggregation.AggregatedPage;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;

import java.util.*;

import static com.github.wz2cool.elasticsearch.helper.CommonsHelper.getPropertyName;

public abstract class ElasticsearchExtensionRepository<T> {

    private ElasticsearchTemplate elasticsearchTemplate;

    public ElasticsearchExtensionRepository(ElasticsearchTemplate elasticsearchTemplate) {
        this.elasticsearchTemplate = elasticsearchTemplate;
    }

    public LogicPagingResult<T> selectByLogicPaging(LogicPagingQuery<T> logicPagingQuery) {
        int pageSize = logicPagingQuery.getPageSize();
        int queryPageSize = pageSize + 1;
        final BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        Map.Entry<SortDescriptor, QueryBuilder> mapEntry = LogicPagingHelper.getPagingSortFilterMap(
                logicPagingQuery.getPagingPropertyFunc(),
                logicPagingQuery.getSortOrder(),
                logicPagingQuery.getLastStartPageId(),
                logicPagingQuery.getLastEndPageId(),
                logicPagingQuery.getUpDown());
        if (Objects.nonNull(logicPagingQuery.getQueryBuilder())) {
            boolQueryBuilder.must(logicPagingQuery.getQueryBuilder());
        }
        if (Objects.nonNull(mapEntry.getValue())) {
            boolQueryBuilder.must(mapEntry.getValue());
        }
        NativeSearchQueryBuilder esQuery = new NativeSearchQueryBuilder();
        esQuery.withQuery(boolQueryBuilder);
        esQuery.withPageable(PageRequest.of(0, queryPageSize));
        esQuery.withSort(SortBuilders.fieldSort(getPropertyName(logicPagingQuery.getPagingPropertyFunc()))
                .order(logicPagingQuery.getSortOrder()));
        AggregatedPage<T> ts = this.elasticsearchTemplate.queryForPage(
                esQuery.build(), logicPagingQuery.getClazz(), logicPagingQuery.getResultsMapper());
        List<T> dataList = ts.getContent();
        if (!logicPagingQuery.getSortOrder().equals(mapEntry.getKey().getSortOrder())) {
            Collections.reverse(dataList);
        }
        Optional<LogicPagingResult<T>> logicPagingResultOptional = LogicPagingHelper.getPagingResult(
                logicPagingQuery.getPagingPropertyFunc(),
                dataList, logicPagingQuery.getPageSize(), logicPagingQuery.getUpDown());
        if (logicPagingResultOptional.isPresent()) {
            return logicPagingResultOptional.get();
        }
        LogicPagingQuery<T> resetPagingQuery = LogicPagingQuery.createQuery(
                logicPagingQuery.getClazz(),
                logicPagingQuery.getPagingPropertyFunc(),
                logicPagingQuery.getSortOrder(),
                UpDown.NONE);
        resetPagingQuery.setPageSize(logicPagingQuery.getPageSize());
        resetPagingQuery.setQueryBuilder(logicPagingQuery.getQueryBuilder());
        return selectByLogicPaging(resetPagingQuery);
    }


}
