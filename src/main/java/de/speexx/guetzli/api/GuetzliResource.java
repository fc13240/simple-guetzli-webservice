/*
 * A simple wrapper for Googles guetzli JPEG compressor.
 * Copyright (C) 2017 Sascha Kohlmann
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.speexx.guetzli.api;

import de.speexx.guetzli.service.ImageMetadata;
import de.speexx.guetzli.service.ImageService;
import de.speexx.guetzli.service.ImageType;
import de.speexx.guetzli.service.ProcessStatus;
import de.speexx.guetzli.service.event.ContentEvent;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedReader;
import java.io.PipedWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URI;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.ejb.Stateless;
import javax.enterprise.event.Event;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.stream.JsonGenerator;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import de.speexx.guetzli.service.event.NewContent;

/**
 * Handle s the access to the <code>guetzli</code> remote transformation system.
 * The process works in the following steps:
 * <ul>
 *   <li>{@linkplain #uploadImage(java.io.InputStream, java.lang.String, long, java.lang.String) upload} and image
 *     of mime type <em>image/png</em> or <em>image/jpeg</em> to the remote system. The upload
 *     must be a <code>POST</code> HTTP call. The reponse header contains a <code>location</code> entry
 *     with a link to the uploaded image.</li>
 *   <li>Check the {@linkplain #getMeta(java.lang.String)  meta data} for the status. Status can be
 *     <dl>
 *       <dt>{@linkplain ProcessStatus#stored stored}</dt>
 *       <dd>The image is correct uploaded and stored in the backend system.</dd>
 *       <dt>{@linkplain ProcessStatus#waiting waiting}</dt>
 *       <dd>The uploaded image is enqueued to be transformed.</dd>
 *       <dt>{@linkplain ProcessStatus#transforming transforming}</dt>
 *       <dd>The uploaded image is in the transformation process.</dd>
 *       <dt>{@linkplain ProcessStatus#transformed transformed}</dt>
 *       <dd>The uploaded image was successfull transformed.</dd>
 *       <dt>{@linkplain ProcessStatus#failed failed}</dt>
 *       <dd>The transformation process failed for different reasons.</dd>
 *     </dl>
 *     The meta data can be downloaded with replacing the <code>source</code> part of the <code>location</code>
 *     URL of step 1 with th literal <code>meta</code>.</li>
 *   <li>{@linkplain #getSourceImage(java.lang.String, java.lang.String) Download} a successful transformed image. To download the successful transformed image, replace the
 *     <code>source</code> part of the URL from the <code>location</code> header of step 1 with <code>target</code>.</li>
 * </ul>
 * <p>The URL part infront of the {@literal source} literal of the <code>location</code> header is the content ID of
 * the upload and the main reference of all additional REST commands.</p>
 * @author sascha.kohlmann
 */
@Path("/image")
@Stateless
public class GuetzliResource {
    
    private static Logger LOG = Logger.getLogger(GuetzliResource.class.getSimpleName());

    private static final long MAX_SIZE_IN_MB = 8;
    private static final long KIB = 1024;
    private static final long MAX_SIZE_IN_BYTE = KIB * KIB * MAX_SIZE_IN_MB;
    private static final String MEDIA_TYPE_PNG = "image/png";
    private static final String MEDIA_TYPE_JPEG = "image/jpeg";
    
    @Context private UriInfo uriInfo;
    @Inject private ImageService imgSrv;
    
    @Inject @NewContent private Event<ContentEvent> imageEvents;

    /**
     * Download the {@linkplain #uploadImage(java.io.InputStream, java.lang.String, long, java.lang.String) uploaded}
     * source image. The GET URL can have an optional query parameter <code>download</code>. If the value of 
     * <code>download</code> is {@literal true} the repsonse header may contain the <em>Content-Disposition</em>
     * header with the filename of the uploaded image.
     * @param contentId the ID of the uploaded image.
     * @param download indicates that the <em>Content-Disposition</em> header should be set. Can be {@literal true}
     *                 to may be set the header. All other values will be interpreted as {@literal false}.
     * @return contains the source image or a failure message. HTTP reponse might be 200, 404 or 500.
     * @see #getTargetImage(java.lang.String, java.lang.String) 
     */
    @GET
    @Path("{contentId}/source")
    @Produces({"image/jpeg", "image/png"})
    public Response getSourceImage(final @PathParam("contentId") String contentId, @DefaultValue("no") @QueryParam("download") String download) {
        try {
            final ImageMetadata meta = this.imgSrv.getMetadata(contentId);
            return getImage(contentId, Type.source, meta.getSourceType(), isDownloadable(download));
        } catch (final IOException e) {
            if (e instanceof FileNotFoundException) {
                throw new WebApplicationException(Response.status(Response.Status.NOT_FOUND)
                                                          .type(MediaType.TEXT_PLAIN)
                                                          .entity("No source image for ID " + contentId)
                                                          .build());
            }
            throw new WebApplicationException(Response.serverError()
                                                      .type(MediaType.TEXT_PLAIN)
                                                      .entity("Unable to get source image for ID " + contentId)
                                                      .build());
        }
    }

    /**
     * Download the transformed {@linkplain #uploadImage(java.io.InputStream, java.lang.String, long, java.lang.String) uploaded}
     * target image if available. The GET URL can have an optional query parameter <code>download</code>. If the value of 
     * <code>download</code> is {@literal true} the repsonse header may contain the <em>Content-Disposition</em>
     * header with the filename of the uploaded image.
     * @param contentId the ID of the uploaded image.
     * @param download indicates that the <em>Content-Disposition</em> header should be set. Can be {@literal true}
     *                 to may be set the header. All other values will be interpreted as {@literal false}.
     * @return contains the target image or a failure message. HTTP reponse might be 200, 404 or 500.
     * @see #getSourceImage(java.lang.String, java.lang.String) 
     */
    @GET
    @Path("{contentId}/target")
    @Produces({"image/jpeg", "image/png"})
    public Response getTargetImage(final @PathParam("contentId") String contentId, @DefaultValue("false") @QueryParam("download") String download) {
        return getImage(contentId, Type.target, ImageType.JPG, isDownloadable(download));
    }

    /**
     * Common method to get the <em>source</em> or transformed <em>traget</em> image.
     * @see #getSourceImage(java.lang.String, java.lang.String)
     * @see #getTargetImage(java.lang.String, java.lang.String)
     */    
    Response getImage(final String contentId, final Type type, final ImageType imageType, final boolean download) {
        assert contentId != null;
        assert type != null;
        assert imageType != null;
        
        try {
            final InputStream in;
            switch (type) {
                case source:
                    in = this.imgSrv.getSourceImage(contentId);
                    break;
                case target:
                    in = this.imgSrv.getTargetImage(contentId);
                    break;
                default:
                    throw new WebApplicationException(Response.serverError()
                                                              .entity("Image type '" + type + "' not supported. Must be 'source' or 'target'.")
                                                              .build());
            }
            final Response.ResponseBuilder builder = Response.ok(in).type(imageType.getMimeType());
            if (download) {
                enhanceContentDisposition(contentId, builder);
            }

            return builder.build();

        } catch (final IOException e) {
            if (e instanceof FileNotFoundException) {
                throw new WebApplicationException(Response.status(Response.Status.NOT_FOUND)
                                                          .type(MediaType.TEXT_PLAIN)
                                                          .entity("No target image for ID " + contentId)
                                                          .build());
            }
            throw new WebApplicationException(Response.serverError()
                                                      .entity("Unable to get target image for ID " + contentId)
                                                      .type(MediaType.TEXT_PLAIN)
                                                      .build());
        }
    }

    /**
     * Enhance the given <code>ResponseBuilder</code> with the <em>Content-Disposition</em> header if possible.
     * @param contentId the content ID
     * @param builder the builder
     */
    void enhanceContentDisposition(final String contentId, final Response.ResponseBuilder builder) {
        assert contentId != null;
        assert builder != null;

        try {
            final ImageMetadata meta = this.imgSrv.getMetadata(contentId);
            final String filename = meta.getSourceName();
            builder.header("Content-Disposition", "attachment; filename=\"" + filename +"\"");
        } catch (final IOException e) {
            LOG.log(Level.WARNING, "Unable to load metadata for ID {0}", contentId);
        }
    }

    /**
     * Return the meta data for the given content ID.
     * @param contentId teh content ID to get the metadata for
     * @return contains the metadata in JSON format (HTTP 200) or HTTP error code 404 of the content ID has no
     *         corresponding {@linkplain #uploadImage(java.io.InputStream, java.lang.String, long, java.lang.String)}
     *         or HTTP error code 500 for any other problem.
     */
    @GET
    @Path("{contentId}/meta")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getMeta(final @PathParam("contentId") String contentId) {
        
        try {
            final ImageMetadata meta = this.imgSrv.getMetadata(contentId);
            final PipedReader reader = new PipedReader();
            final Writer writer = new PipedWriter(reader);
            
            try (final JsonGenerator generator = Json.createGenerator(writer);) {
                generator.writeStartObject();

                generator.write("contentId", contentId);
                final ProcessStatus status = meta.getStatus();
                assert status != null;
                generator.write("status", status.name());

                generator.writeStartObject("source");
                final String sourceName = meta.getSourceName();
                if (sourceName != null) {
                    generator.write("name", sourceName);
                }
                final ImageType sourceType = meta.getSourceType();
                if (sourceType != null) {
                    generator.write("mime", sourceType.getMimeType());
                }
                final int sourceQuality = meta.getSourceQuality();
                if (sourceQuality > 0) {
                    generator.write("qualitylevel", sourceQuality);
                }
                final long sourceSize = meta.getSourceSize();
                if (sourceSize > 0) {
                    generator.write("size", sourceSize);
                }
                generator.writeEnd();

                if (status == ProcessStatus.transformed) {
                    generator.writeStartObject("target");
                    final int targetQuality = meta.getTargetQuality();
                    if (targetQuality > 0) {
                        generator.write("qualitylevel", targetQuality);
                    }
                    final long targetSize = meta.getTargetSize();
                    if (targetSize > 0) {
                        generator.write("size", targetSize);
                    }
                    generator.writeEnd();
                }

                generator.writeEnd();
            }
            
            return Response.ok(reader).build();
        } catch (final IOException e) {
            if (e instanceof FileNotFoundException) {
                throw new WebApplicationException(Response.status(Response.Status.NOT_FOUND)
                                                          .type(MediaType.TEXT_PLAIN)
                                                          .entity("No metadata found for ID: " + contentId)
                                                          .build());
            }
            throw new WebApplicationException(Response.serverError()
                                                      .type(MediaType.TEXT_PLAIN)
                                                      .entity("Unable to load metadata for ID " + contentId)
                                                      .build());
        }
    }

    /**
     * Receives an image in format {@literal image/jpeg} or {@literal image/png} to perform a <code>guetzli</code>
     * transformation on it.
     * @param in the stream with the image data.
     * @param fileType the mime type of the uploaded image.
     * @param fileSize the size of the uploaded image. must be not greater 8MiB.
     * @param uploadFileName the optional filename of the uploaded image. Must be in header {@literal X-Guetzli-Img-Name}.
     * @return In case of HTTP code 201 the header contains a {@literal location} header with the download URL
     *         for the uploaded source image.
     */
    @POST
    @Consumes({MEDIA_TYPE_JPEG, MEDIA_TYPE_PNG})
    public Response uploadImage(final InputStream in,
                                final @HeaderParam("Content-Type") String fileType,
                                final @HeaderParam("Content-Length") long fileSize,
                                final @HeaderParam("X-Guetzli-Img-Name") String uploadFileName) {
        
        if (fileSize > MAX_SIZE_IN_BYTE) {
            throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST)
                                                      .type(MediaType.TEXT_PLAIN)
                                                      .entity("Image is larger than " + MAX_SIZE_IN_MB + "MB")
                                                      .build());
        }
        
        try {
            final ImageType type = toImageType(fileType);
            final String imageSourceId = this.imgSrv.newImage(in, fileSize, type, uploadFileName);

            this.imageEvents.fire(new ContentEvent(imageSourceId));
            
            return Response.status(Response.Status.CREATED)
                           .location(URI.create(this.uriInfo.getPath() + "/" + imageSourceId + "/source"))
                           .build();
            
        } catch (final UnsupportedTypeException ex) {
            throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST)
                                                      .type(MediaType.TEXT_PLAIN)
                                                      .entity(ex.getMessage())
                                                      .build());
        } catch (final IOException ex) {
            throw new WebApplicationException(Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                                                      .type(MediaType.TEXT_PLAIN)
                                                      .entity("Request failed.")
                                                      .build());
        }
    }
    
    /**
     * Returns all available content IDs in a JSON array.
     * @return all available content IDs in a JSON array.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getContentIds() {
        final StringWriter writer = new StringWriter();
        try (final JsonGenerator generator = Json.createGenerator(writer);) {
            generator.writeStartObject();

            generator.writeStartArray("ids");
            for (final Iterator<String> itr = this.imgSrv.getContentIds(); itr.hasNext(); ) {
                generator.write(itr.next());
            }

            generator.writeEnd();
            generator.writeEnd();
        }
        
        return Response.ok().entity(writer.toString()).build();
    }

    
    ImageType toImageType(final String contentType) throws UnsupportedTypeException {
        assert contentType != null;
        if (MEDIA_TYPE_PNG.equalsIgnoreCase(contentType)) {
            return ImageType.PNG;
        } else if (MEDIA_TYPE_JPEG.equalsIgnoreCase(contentType)) {
            return ImageType.JPG;
        }
        throw new UnsupportedTypeException("Content-Type '" + contentType + "' not supported.");
    }

    static boolean isDownloadable(final String download) {
        if (download == null) {
            return false;
        }
        return ("yes".equalsIgnoreCase(download) 
                || "true".equalsIgnoreCase(download)
                || "y".equalsIgnoreCase(download)
                || "t".equalsIgnoreCase(download));
    }

    
    private static enum Type {
        source, target;
    }
}
