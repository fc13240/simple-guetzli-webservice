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
package de.speexx.guetzli.service;

import de.speexx.guetzli.model.ImageMetadata;
import de.speexx.guetzli.model.ImageService;
import de.speexx.guetzli.model.ImageType;
import de.speexx.guetzli.model.ProcessStatus;
import de.speexx.guetzli.service.event.ImageEvent;
import de.speexx.guetzli.service.event.NewImage;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedReader;
import java.io.PipedWriter;
import java.io.Writer;
import java.net.URI;
import java.util.logging.Logger;
import javax.enterprise.event.Event;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.stream.JsonGenerator;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

/**
 * @author sascha.kohlmann
 */
@Path("/image")
public class GuetzliResource {
    
    private static Logger LOG = Logger.getLogger(GuetzliResource.class.getSimpleName());

    private static final long MAX_SIZE_IN_MB = 8;
    private static final long KIB = 1024;
    private static final long MAX_SIZE_IN_BYTE = KIB * KIB * MAX_SIZE_IN_MB;
    private static final String MEDIA_TYPE_PNG = "image/png";
    private static final String MEDIA_TYPE_JPEG = "image/jpeg";
    
    @Context private UriInfo uriInfo;
    @Inject private ImageService imgSrv;
    
    @Inject @NewImage private Event<ImageEvent> imageEvents;

    @GET
    @Path("{id}/source")
    @Produces({"image/jpeg", "image/png"})
    public Response getSourceImage(final @PathParam("id") String id) {
        try {
            final ImageMetadata meta = this.imgSrv.getMetadata(id);
            return getImage(id, Type.source, meta.getSourceType());
        } catch (final IOException e) {
            if (e instanceof FileNotFoundException) {
                return Response.status(Response.Status.NOT_FOUND)
                               .type(MediaType.TEXT_PLAIN)
                               .entity("No source image for ID " + id)
                               .build();
            }
            return Response.serverError()
                           .type(MediaType.TEXT_PLAIN)
                           .entity("Unable to get source image for ID " + id)
                           .build();
        }
    }

    @GET
    @Path("{id}/target")
    @Produces({"image/jpeg", "image/png"})
    public Response getTargetImage(final @PathParam("id") String id) {
        return getImage(id, Type.target, ImageType.JPG);
    }
    
    Response getImage(final String id, final Type type, final ImageType imageType) {
        assert id != null;
        assert type != null;
        assert imageType != null;
        
        try {
            final InputStream in;
            if (type == Type.source) {
                in = this.imgSrv.getSourceImage(id);
            } else if (type == Type.target) {
                in = this.imgSrv.getTargetImage(id);
            } else {
                throw new WebApplicationException(Response.serverError()
                                                          .entity("Image type '" + type + "' not supported. Must be 'source' or 'target'.")
                                                          .build());
            }
            return Response.ok(in).type(imageType.getMimeType()).build();

        } catch (final IOException e) {
            if (e instanceof FileNotFoundException) {
                return Response.status(Response.Status.NOT_FOUND)
                               .type(MediaType.TEXT_PLAIN)
                               .entity("No target image for ID " + id)
                               .build();
            }
            return Response.serverError()
                           .entity("Unable to get target image for ID " + id)
                           .type(MediaType.TEXT_PLAIN)
                           .build();
        }
    }

    @GET
    @Path("{id}/meta")
    @Produces(MediaType.TEXT_PLAIN)
    public Response getMeta(final @PathParam("id") String id) {
        
        try {
            final ImageMetadata meta = this.imgSrv.getMetadata(id);
            final PipedReader reader = new PipedReader();
            final Writer writer = new PipedWriter(reader);
            
            try (final JsonGenerator generator = Json.createGenerator(writer);) {
                generator.writeStartObject();
                
                generator.write("id", id);
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
            
            return Response.ok(reader).type(MediaType.TEXT_PLAIN).build();
        } catch (final IOException e) {
            if (e instanceof FileNotFoundException) {
                return Response.status(Response.Status.NOT_FOUND).entity("No metadata found for ID: " + id).build();
            }
            return Response.serverError().entity("Unable to load metadata for ID " + id).build();
        }
    }
    
    @POST
    @Consumes({MEDIA_TYPE_JPEG, MEDIA_TYPE_PNG})
    public Response uploadImage(final InputStream in,
                                final @HeaderParam("Content-Type") String fileType,
                                final @HeaderParam("Content-Length") long fileSize,
                                final @HeaderParam("X-Guetzli-Img-Name") String uploadFileName) {
        
        if (fileSize > MAX_SIZE_IN_BYTE) {
            throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST).entity("Image is larger than " + MAX_SIZE_IN_MB + "MB").build());
        }
        
        try {
            final ImageType type = toImageType(fileType);
            final String imageSourceId = this.imgSrv.newImage(in, fileSize, type, uploadFileName);

            this.imageEvents.fire(new ImageEvent(imageSourceId));
            
            return Response.status(Response.Status.CREATED).location(URI.create(this.uriInfo.getPath() + "/" + imageSourceId + "/source")).build();
            
        } catch (final UnsupportedTypeException ex) {
            throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST).entity(ex.getMessage()).build());
        } catch (final IOException ex) {
            throw new WebApplicationException(Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Request failed.").build());
        }
    }
    
    ImageType toImageType(final String contentType) throws UnsupportedTypeException {
        assert contentType != null;
        if (MEDIA_TYPE_PNG.equalsIgnoreCase(contentType)) {
            return ImageType.JPG;
        } else if (MEDIA_TYPE_JPEG.equalsIgnoreCase(contentType)) {
            return ImageType.JPG;
        }
        throw new UnsupportedTypeException("Content-Type '" + contentType + "' not supported.");
    }
    
    private static enum Type {
        source, target;
    }
}
