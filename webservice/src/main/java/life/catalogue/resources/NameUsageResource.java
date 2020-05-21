package life.catalogue.resources;

import com.codahale.metrics.annotation.Timed;
import com.google.common.base.Joiner;
import com.google.common.collect.Streams;
import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.NameUsage;
import life.catalogue.api.model.NameUsageBase;
import life.catalogue.api.model.Page;
import life.catalogue.api.model.ResultPage;
import life.catalogue.api.search.*;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.db.mapper.NameUsageWrapperMapper;
import life.catalogue.dw.jersey.MoreMediaTypes;
import life.catalogue.es.InvalidQueryException;
import life.catalogue.es.NameUsageSearchService;
import life.catalogue.es.NameUsageSuggestionService;
import org.apache.ibatis.session.SqlSession;
import org.gbif.nameparser.api.Rank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

@Produces(MediaType.APPLICATION_JSON)
@Path("/dataset/{datasetKey}/nameusage")
public class NameUsageResource {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(NameUsageResource.class);
  private static final Joiner COMMA_CAT = Joiner.on(';').skipNulls();
  private static final Object[][] EXPORT_HEADERS = new Object[1][];
  private static final Object[][] EXPORT_HEADERS_ISSUES = new Object[1][];
  static {
    EXPORT_HEADERS[0] = new Object[]{"ID", "parentID", "status", "rank", "scientificName", "authorship"};
    EXPORT_HEADERS_ISSUES[0] = Arrays.copyOf(EXPORT_HEADERS[0], 7);
    EXPORT_HEADERS_ISSUES[0][6] = "issues";
  }
  private final NameUsageSearchService searchService;
  private final NameUsageSuggestionService suggestService;

  public NameUsageResource(NameUsageSearchService search, NameUsageSuggestionService suggest) {
    this.searchService = search;
    this.suggestService = suggest;
  }

  @GET
  public ResultPage<NameUsageBase> list(@PathParam("datasetKey") int datasetKey, @Valid Page page, @Context SqlSession session) {
    Page p = page == null ? new Page() : page;
    NameUsageMapper mapper = session.getMapper(NameUsageMapper.class);
    List<NameUsageBase> result = mapper.list(datasetKey, p);
    return new ResultPage<>(p, result, () -> mapper.count(datasetKey));
  }

  @GET
  @Produces({MoreMediaTypes.TEXT_CSV, MoreMediaTypes.TEXT_TSV})
  public Stream<Object[]> exportCsv(@PathParam("datasetKey") int datasetKey,
                                    @QueryParam("issue") boolean withIssues,
                                    @QueryParam("min") Rank min,
                                    @QueryParam("max") Rank max,
                                    @Context SqlSession session) {
    if (withIssues) {
      NameUsageWrapperMapper nuwm = session.getMapper(NameUsageWrapperMapper.class);
      return Stream.concat(
        Stream.of(EXPORT_HEADERS_ISSUES),
        Streams.stream(nuwm.processDatasetUsageWithIssues(datasetKey)).map(this::map)
      );
    } else {
      NameUsageMapper num = session.getMapper(NameUsageMapper.class);
      return Stream.concat(
        Stream.of(EXPORT_HEADERS),
        Streams.stream(num.processDataset(datasetKey, min, max)).map(this::map)
      );
    }
  }

  private Object[] map(NameUsageBase nu){
    return new Object[]{
      nu.getId(),
      nu.getParentId(),
      nu.getStatus(),
      nu.getName().getRank(),
      nu.getName().getScientificName(),
      nu.getName().getAuthorship()
    };
  }

  private Object[] map(NameUsageWrapper nuw){
    NameUsageBase nu = (NameUsageBase) nuw.getUsage();
    return new Object[]{
      nu.getId(),
      nu.getParentId(),
      nu.getStatus(),
      nu.getName().getRank(),
      nu.getName().getScientificName(),
      nu.getName().getAuthorship(),
      COMMA_CAT.join(nuw.getIssues())
    };
  }

  @GET
  @Path("{id}")
  public NameUsageWrapper getByID(@PathParam("datasetKey") int datasetKey, @PathParam("id") String id) {
    NameUsageSearchRequest req = new NameUsageSearchRequest();
    req.addFilter(NameUsageSearchParameter.DATASET_KEY, datasetKey);
    req.addFilter(NameUsageSearchParameter.USAGE_ID, id);
    ResultPage<NameUsageWrapper> results = searchService.search(req, new Page());
    if (results.size()==1) {
      return results.getResult().get(0);
    }
    throw NotFoundException.notFound(NameUsage.class, datasetKey, id);
  }

  @GET
  @Timed
  @Path("search")
  public ResultPage<NameUsageWrapper> searchDataset(@PathParam("datasetKey") int datasetKey,
                                                    @BeanParam NameUsageSearchRequest query,
                                                    @Valid @BeanParam Page page,
                                                    @Context UriInfo uri) throws InvalidQueryException {
    query.addFilters(uri.getQueryParameters());
    if (query.hasFilter(NameUsageSearchParameter.DATASET_KEY)) {
      throw new IllegalArgumentException("No further datasetKey parameter allowed, search already scoped to datasetKey=" + datasetKey);
    }
    query.addFilter(NameUsageSearchParameter.DATASET_KEY, datasetKey);
    return searchService.search(query, page);
  }

  @POST
  @Path("search")
  public ResultPage<NameUsageWrapper> searchPOST(@PathParam("datasetKey") int datasetKey,
                                                 @Valid NameUsageSearchResource.SearchRequestBody req,
                                                 @Context UriInfo uri) throws InvalidQueryException {
    return searchDataset(datasetKey, req.request, req.page, uri);
  }

  @GET
  @Timed
  @Path("suggest")
  public NameUsageSuggestResponse suggestDataset(@PathParam("datasetKey") int datasetKey,
                                                 @BeanParam NameUsageSuggestRequest query) throws InvalidQueryException {
    if (query.getDatasetKey() != null && !query.getDatasetKey().equals(datasetKey)) {
      throw new IllegalArgumentException("No further datasetKey parameter allowed, suggest already scoped to datasetKey=" + datasetKey);
    }
    query.setDatasetKey(datasetKey);
    return suggestService.suggest(query);
  }
}
